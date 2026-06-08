package io.github.edadma.hocon

import scala.collection.immutable.ListMap
import scala.collection.mutable
import Token.*

/** Recursive-descent parser turning a [[Tok]] stream into a [[Config]].
  *
  * The grammar handled here is the i18n-usable core of HOCON: an optional set of root braces,
  * `=`/`:` separators (or none before a `{`), newline-or-comma separated fields with optional
  * trailing commas, arrays, quoted and unquoted scalar values, path-expression keys
  * (`a.b.c = v`) that expand into nested objects, substitutions, and whitespace-aware value
  * concatenation. Within a single document, a key that recurs with an object value deep-merges; any
  * other recurrence replaces.
  *
  * `include` directives are expanded here, at parse time: the named resource is fetched through the
  * [[ConfigSource]], parsed (still unresolved), and deep-merged at the point it appears, so later
  * fields override it and substitutions see the combined tree. `includeStack` carries the chain of
  * specs currently being expanded so a cycle of mutually-including files is caught rather than
  * looping forever.
  */
final class Parser(
    tokens: Vector[Tok],
    source: ConfigSource = ConfigSource.empty,
    includeStack: Set[String] = Set.empty,
):

  private val includeQualifiers = Set("required", "file", "url", "classpath")

  private var idx = 0

  private def tk: Token         = tokens(idx).token
  private def tokenAt(j: Int): Token = if j < tokens.length then tokens(j).token else EOF
  private def error(msg: String): Nothing =
    val t = tokens(idx)
    throw ParseError(msg, t.line, t.col)

  private def isWs: Boolean = tk match
    case Whitespace(_) => true
    case _             => false

  private def skipWs(): Unit         = while isWs do idx += 1
  private def skipSeparators(): Unit = while isWs || tk == Newline || tk == Comma do idx += 1

  /** Parse the whole input into an unresolved root object — substitution nodes are left in place for
    * the resolver to eliminate.
    */
  def parseRoot(): ConfigObject =
    skipSeparators()
    val rootObj =
      if tk == LBrace then
        idx += 1
        val o = parseObjectBody(braced = true)
        if tk != RBrace then error("expected '}'")
        idx += 1
        o
      else parseObjectBody(braced = false)
    skipSeparators()
    if tk != EOF then error("unexpected token after configuration")
    rootObj

  private def parseObjectBody(braced: Boolean): ConfigObject =
    var fields: Map[String, ConfigValue] = ListMap.empty
    var done                             = false
    while !done do
      skipSeparators()
      tk match
        case EOF =>
          if braced then error("unclosed object — expected '}'")
          done = true
        case RBrace =>
          if braced then done = true // caller consumes the brace
          else error("unexpected '}'")
        case _ =>
          if isIncludeDirective then
            val included = parseInclude()
            fields = ConfigObject.deepMerge(ConfigObject(fields), included).fields
          else fields = parseField(fields)
    ConfigObject(fields)

  /** `include` is a directive only when it is followed by a quoted resource name or one of the
    * qualifier forms `required(...)`, `file(...)`, `url(...)`, `classpath(...)`. Followed by anything
    * else — `=`, `:`, `{` — it is an ordinary key named `include`.
    */
  private def isIncludeDirective: Boolean = tk match
    case Unquoted("include") =>
      tokenAt(nextSignificant(idx + 1)) match
        case Quoted(_)                          => true
        case Unquoted(q) if isQualifierPrefix(q) => true
        case _                                  => false
    case _ => false

  /** A qualifier prefix is one or more `name(` run together — `file(`, `required(`, even
    * `required(file(` — since both letters and parens lex into a single unquoted run.
    */
  private def isQualifierPrefix(q: String): Boolean =
    q.endsWith("(") && q.split('(').forall(s => s.isEmpty || includeQualifiers(s))

  private def nextSignificant(from: Int): Int =
    var j = from
    while (tokenAt(j) match { case Whitespace(_) => true; case _ => false }) do j += 1
    j

  /** Parse an `include` directive starting at the `include` keyword and return the included object
    * (already deep-merged from any nested includes, with substitutions left unresolved). The
    * qualifier forms may nest one inside `required(...)`; each opens a paren that is balanced after
    * the quoted spec is read.
    */
  private def parseInclude(): ConfigObject =
    idx += 1 // consume "include"
    skipWs()
    var depth    = 0
    var required = false
    var kind     = IncludeKind.Heuristic
    var spec     = ""
    var gotSpec  = false
    while !gotSpec do
      tk match
        case Quoted(s) =>
          idx += 1
          spec = s
          gotSpec = true
        case Unquoted(q) if isQualifierPrefix(q) =>
          idx += 1
          for name <- q.split('(').toList.filter(_.nonEmpty) do
            depth += 1
            name match
              case "required"  => required = true
              case "file"      => kind = IncludeKind.File
              case "url"       => kind = IncludeKind.Url
              case "classpath" => kind = IncludeKind.Classpath
              case _           => // already filtered by isQualifierPrefix
          skipWs()
        case _ => error("include expects a quoted resource name")
    consumeCloseParens(depth)
    expandInclude(kind, spec, required)

  private def consumeCloseParens(count: Int): Unit =
    var remaining = count
    while remaining > 0 do
      skipWs()
      tk match
        case Unquoted(t) if t.nonEmpty && t.forall(_ == ')') =>
          if t.length > remaining then error("unbalanced ')' in include")
          remaining -= t.length
          idx += 1
        case _ => error("expected ')' to close an include qualifier")

  private def expandInclude(kind: IncludeKind, spec: String, required: Boolean): ConfigObject =
    if includeStack.contains(spec) then
      throw IncludeException(s"circular include: ${(includeStack.toList :+ spec).mkString(" -> ")}")
    source.load(kind, spec) match
      case Some(text) => Parser(Lexer(text).tokenize(), source, includeStack + spec).parseRoot()
      case None =>
        if required then throw IncludeException(s"required include not found: $spec")
        else ConfigObject.empty

  /** Parse one field — `key = value`, `key : value`, `key { … }`, or `key += value` — and fold it
    * into `fields`. The `+=` form appends to the array already at `key` (or starts a fresh array when
    * the key is absent), which is HOCON's self-referential-array shorthand for `key = ${?key} [value]`.
    */
  private def parseField(fields: Map[String, ConfigValue]): Map[String, ConfigValue] =
    val path = parsePath()
    skipWs()
    tk match
      case Colon | Equals =>
        idx += 1
        insert(fields, path, spliceSelfRef(fields, path, parseValue()))
      case LBrace =>
        insert(fields, path, parseValue()) // object value with no separator
      case PlusEquals =>
        idx += 1
        insert(fields, path, ConfigSelfAppend(parseValue()))
      case _ => error("expected '=', ':', '+=', or '{' after key")

  /** Resolve a field's self-reference against its own previous value, HOCON's "look backward" rule:
    * `a = ${a}` and `path = ${path} [x]` use the value `a`/`path` already held in this object body,
    * rather than the not-yet-stored value being defined now (which would be an unbreakable cycle).
    * A `${?self}` with no prior value disappears; a required `${self}` with no prior is left in place
    * for the resolver to report as a cycle. The self-path is matched against this field's path within
    * the current object body — a top-level field's path is its absolute path, so the common case is
    * exact.
    */
  private def spliceSelfRef(
      fields: Map[String, ConfigValue],
      path: List[String],
      value: ConfigValue,
  ): ConfigValue =
    val selfPath = path.mkString(".")
    val prior    = lookupPath(fields, path)
    def go(v: ConfigValue): ConfigValue = v match
      case ConfigSubstitution(p, optional) if p == selfPath =>
        prior match
          case Some(pv)          => pv
          case None if optional  => ResolveMissing
          case None              => v
      case ConfigConcat(parts) => ConfigConcat(parts.map(go))
      case other               => other
    go(value)

  /** Parse a key as a HOCON path expression: a run of quoted and unquoted pieces in which an unquoted
    * `.` separates path elements and a quoted segment's `.` is literal. `foo.bar` and `foo."bar.baz"`
    * and `a b c` are all valid keys — the first nests two levels, the second is a two-element path
    * whose second element literally contains a dot, the third is the single key `a b c`. Whitespace
    * between pieces of one element is preserved; whitespace at an element's edges is discarded.
    */
  private def parsePath(): List[String] =
    val segments = scala.collection.mutable.ListBuffer.empty[String]
    val cur      = StringBuilder()
    var hasText  = false  // whether the current element has any non-whitespace content yet
    var pending  = ""     // whitespace seen since the last text, committed only if more text follows
    var started  = false

    def addText(s: String): Unit =
      if hasText then cur ++= pending
      pending = ""
      cur ++= s
      hasText = true

    def endElement(): Unit =
      segments += cur.toString
      cur.clear()
      hasText = false
      pending = ""

    skipWs() // leading whitespace before the key
    var continue = true
    while continue do
      tk match
        case Quoted(v) =>
          idx += 1; started = true; addText(v)
        case Whitespace(w) =>
          idx += 1; if hasText then pending += w // edge whitespace is dropped, interior preserved
        case Unquoted(t) =>
          idx += 1; started = true
          val parts = splitKeepingEmpty(t, '.')
          for (p, i) <- parts.zipWithIndex do
            if i > 0 then endElement() // an unquoted '.' closes the current element
            if p.nonEmpty then addText(p)
        case _ => continue = false
    if !started then error("expected a key")
    endElement()
    val result = segments.toList
    if result.exists(_.isEmpty) then error(s"invalid key — empty path element in '${result.mkString(".")}'")
    result

  /** The value currently stored at `path` while a single object body is being built, used to resolve
    * the `${?key}` an `+=` desugars to against the prior definition. Returns `None` if nothing is yet
    * at `path` (or an intermediate segment is not an object).
    */
  private def lookupPath(fields: Map[String, ConfigValue], path: List[String]): Option[ConfigValue] =
    path match
      case Nil        => None
      case key :: Nil => fields.get(key)
      case key :: rest =>
        fields.get(key) match
          case Some(o: ConfigObject) => lookupPath(o.fields, rest)
          case _                     => None

  /** Parse a value, which may be a whitespace-separated concatenation of pieces. Each piece is an
    * object, an array, a quoted/unquoted scalar, or a substitution; interior whitespace is captured
    * so a string concatenation can preserve it, and leading/trailing whitespace is trimmed. A single
    * piece is returned bare; two or more become a [[ConfigConcat]] for the resolver to collapse.
    */
  private def parseValue(): ConfigValue =
    skipWs()
    val parts    = mutable.ArrayBuffer.empty[ConfigValue]
    var continue = true
    while continue do
      tk match
        case Newline | Comma | RBrace | RBracket | EOF => continue = false
        case Whitespace(w)                             => idx += 1; parts += ConfigWhitespace(w)
        case LBrace =>
          idx += 1
          val o = parseObjectBody(braced = true)
          if tk != RBrace then error("expected '}'")
          idx += 1
          parts += o
        case LBracket      => parts += parseArray()
        case Quoted(v)     => idx += 1; parts += ConfigString(v)
        case Subst(p, opt) => idx += 1; parts += ConfigSubstitution(p, opt)
        case Unquoted(t)   => idx += 1; parts += classify(t)
        case _             => error("unexpected token in value")
    val trimmed = parts.toList.dropWhile(isWhitespacePart).reverse.dropWhile(isWhitespacePart).reverse
    trimmed match
      case Nil           => ConfigString("")
      case single :: Nil => single
      case many          => ConfigConcat(many)

  private def parseArray(): ConfigArray =
    idx += 1 // consume '['
    val elems = List.newBuilder[ConfigValue]
    var done  = false
    while !done do
      skipSeparators()
      tk match
        case RBracket => idx += 1; done = true
        case EOF      => error("unclosed array — expected ']'")
        case _        => elems += parseValue()
    ConfigArray(elems.result())

  private def isWhitespacePart(v: ConfigValue): Boolean = v match
    case _: ConfigWhitespace => true
    case _                   => false

  private def classify(raw: String): ConfigValue = raw match
    case "true"            => ConfigBoolean(true)
    case "false"           => ConfigBoolean(false)
    case "null"            => ConfigNull
    case s if isNumber(s)  => ConfigNumber(s)
    case s                 => ConfigString(s)

  /** Whether `s` is a JSON/HOCON number literal — `-?digits(.digits)?([eE][+-]?digits)?` — written
    * as a hand scan so the core depends on no regex engine and behaves identically on every platform.
    */
  private def isNumber(s: String): Boolean =
    val n = s.length
    var i = 0
    def digits(): Boolean =
      val start = i
      while i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9' do i += 1
      i > start
    if i < n && s.charAt(i) == '-' then i += 1
    if !digits() then return false
    if i < n && s.charAt(i) == '.' then
      i += 1
      if !digits() then return false
    if i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E') then
      i += 1
      if i < n && (s.charAt(i) == '+' || s.charAt(i) == '-') then i += 1
      if !digits() then return false
    i == n

  /** Split on a single character, keeping every empty field including trailing ones — unlike
    * `StringOps.split(Char)`, which drops trailing empties — so `a..b` and a trailing `.` are visible
    * as empty path elements.
    */
  private def splitKeepingEmpty(s: String, sep: Char): Array[String] =
    val out = scala.collection.mutable.ArrayBuffer.empty[String]
    val cur = StringBuilder()
    for c <- s do
      if c == sep then { out += cur.toString; cur.clear() }
      else cur += c
    out += cur.toString
    out.toArray

  /** Insert `value` at `path` into `fields`, creating intermediate objects and deep-merging when an
    * object meets an existing object at the same key.
    */
  private def insert(
      fields: Map[String, ConfigValue],
      path: List[String],
      value: ConfigValue,
  ): Map[String, ConfigValue] =
    path match
      case Nil => fields
      case key :: Nil =>
        val merged = value match
          case ConfigSelfAppend(elem) => ConfigObject.appendInto(fields.get(key), elem)
          case n: ConfigObject =>
            fields.get(key) match
              case Some(o: ConfigObject) => ConfigObject.deepMerge(o, n)
              case _                     => n
          case _ => value
        fields.updated(key, merged)
      case key :: rest =>
        val child = fields.get(key) match
          case Some(o: ConfigObject) => o
          case _                     => ConfigObject.empty
        fields.updated(key, ConfigObject(insert(child.fields, rest, value)))
