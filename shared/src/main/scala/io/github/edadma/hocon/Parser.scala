package io.github.edadma.hocon

import scala.collection.immutable.ListMap
import scala.collection.mutable
import Token.*

/** Recursive-descent parser turning a [[Tok]] stream into a [[Config]].
  *
  * The grammar handled here is the i18n-usable core of HOCON: an optional set of root braces,
  * `=`/`:` separators (or none before a `{`), newline-or-comma separated fields with optional
  * trailing commas, arrays, quoted and unquoted scalar values, and path-expression keys
  * (`a.b.c = v`) that expand into nested objects. Within a single document, a key that recurs with
  * an object value deep-merges; any other recurrence replaces. Whitespace-aware value concatenation,
  * substitutions, durations and includes are later phases — an unquoted value is read to its
  * terminator and trimmed.
  */
final class Parser(tokens: Vector[Tok]):

  private val numberRe = "^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$".r

  private var idx = 0

  private def tk: Token         = tokens(idx).token
  private def error(msg: String): Nothing =
    val t = tokens(idx)
    throw ParseError(msg, t.line, t.col)

  private def isWs: Boolean = tk match
    case Whitespace(_) => true
    case _             => false

  private def skipWs(): Unit         = while isWs do idx += 1
  private def skipSeparators(): Unit = while isWs || tk == Newline || tk == Comma do idx += 1

  /** Parse the whole input into a `Config`. */
  def parse(): Config =
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
    Config(rootObj)

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
          val (path, value) = parseEntry()
          fields = insert(fields, path, value)
    ConfigObject(fields)

  private def parseEntry(): (List[String], ConfigValue) =
    val path = parseKey()
    skipWs()
    tk match
      case Colon | Equals =>
        idx += 1
        (path, parseValue())
      case LBrace =>
        (path, parseValue()) // object value with no separator
      case _ => error("expected '=', ':', or '{' after key")

  private def parseKey(): List[String] =
    skipWs()
    tk match
      case Quoted(v) =>
        idx += 1
        List(v)
      case Unquoted(t) =>
        idx += 1
        val parts = t.split("\\.", -1).toList
        if parts.exists(_.isEmpty) then error(s"invalid key '$t'")
        parts
      case _ => error("expected a key")

  private def parseValue(): ConfigValue =
    skipWs()
    tk match
      case LBrace =>
        idx += 1
        val o = parseObjectBody(braced = true)
        if tk != RBrace then error("expected '}'")
        idx += 1
        o
      case LBracket                                => parseArray()
      case Newline | Comma | RBrace | RBracket | EOF => ConfigString("")
      case _                                       => parseSimpleValue()

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

  private def isWsTok(t: Token): Boolean = t match
    case Whitespace(_) => true
    case _             => false

  /** Collect a scalar value's tokens up to the next terminator, then classify the trimmed text. A
    * lone quoted string keeps its exact contents; anything else is trimmed and read as
    * true/false/null, a number, or an unquoted string.
    */
  private def parseSimpleValue(): ConfigValue =
    val collected = mutable.ArrayBuffer.empty[Token]
    var continue  = true
    while continue do
      tk match
        case Newline | Comma | RBrace | RBracket | LBrace | LBracket | EOF => continue = false
        case other => collected += other; idx += 1
    val trimmed = collected.dropWhile(isWsTok).toList.reverse.dropWhile(isWsTok).reverse
    trimmed match
      case Quoted(v) :: Nil => ConfigString(v)
      case _ =>
        val raw = trimmed.map {
          case Unquoted(t)   => t
          case Whitespace(w) => w
          case Quoted(v)     => v
          case _             => ""
        }.mkString
        classify(raw)

  private def classify(raw: String): ConfigValue = raw match
    case "true"                       => ConfigBoolean(true)
    case "false"                      => ConfigBoolean(false)
    case "null"                       => ConfigNull
    case s if numberRe.matches(s)     => ConfigNumber(s)
    case s                            => ConfigString(s)

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
        val merged = (fields.get(key), value) match
          case (Some(o: ConfigObject), n: ConfigObject) => ConfigObject.deepMerge(o, n)
          case _                                        => value
        fields.updated(key, merged)
      case key :: rest =>
        val child = fields.get(key) match
          case Some(o: ConfigObject) => o
          case _                     => ConfigObject.empty
        fields.updated(key, ConfigObject(insert(child.fields, rest, value)))
