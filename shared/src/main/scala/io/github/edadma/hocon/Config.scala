package io.github.edadma.hocon

import scala.concurrent.duration.FiniteDuration

/** An immutable, untyped view over a parsed HOCON document.
  *
  * Paths are dot-separated expressions (`a.b.c`). Typed getters interpret the value at a path and
  * throw [[MissingPathException]] when nothing is there and [[WrongTypeException]] when the value is
  * the wrong shape; the `*Opt` variants return `None` instead of throwing on a missing path.
  */
final case class Config(root: ConfigObject):

  private def find(path: String): Option[ConfigValue] =
    def go(cv: ConfigValue, segs: List[String]): Option[ConfigValue] = segs match
      case Nil => Some(cv)
      case k :: rest =>
        cv match
          case o: ConfigObject => o.fields.get(k).flatMap(go(_, rest))
          case _               => None
    if path.isEmpty then Some(root) else go(root, path.split('.').toList)

  /** True when `path` resolves to a present, non-null value. */
  def hasPath(path: String): Boolean = find(path).exists(_ != ConfigNull)

  private def requirePath(path: String): ConfigValue = find(path) match
    case None | Some(ConfigNull) => throw MissingPathException(path)
    case Some(v)                 => v

  private def typeName(v: ConfigValue): String = ConfigValue.typeName(v)

  def getString(path: String): String = requirePath(path) match
    case ConfigString(s)  => s
    case ConfigNumber(r)  => r
    case ConfigBoolean(b) => b.toString
    case other            => throw WrongTypeException(path, "a string", typeName(other))

  private def numericRaw(path: String): String = requirePath(path) match
    case ConfigNumber(r) => r
    case ConfigString(s) => s
    case other           => throw WrongTypeException(path, "a number", typeName(other))

  private def parseNum[A](path: String, ty: String)(f: String => A): A =
    val r = numericRaw(path)
    try f(r)
    catch case _: NumberFormatException => throw WrongTypeException(path, ty, "string")

  def getInt(path: String): Int       = parseNum(path, "an int")(BigDecimal(_).toInt)
  def getLong(path: String): Long     = parseNum(path, "a long")(BigDecimal(_).toLong)
  def getDouble(path: String): Double = parseNum(path, "a double")(_.toDouble)

  def getBoolean(path: String): Boolean = requirePath(path) match
    case ConfigBoolean(b) => b
    case ConfigString(s) =>
      s.toLowerCase match
        case "true" | "yes" | "on"  => true
        case "false" | "no" | "off" => false
        case _                      => throw WrongTypeException(path, "a boolean", "string")
    case other => throw WrongTypeException(path, "a boolean", typeName(other))

  def getConfig(path: String): Config = requirePath(path) match
    case o: ConfigObject => Config(o)
    case other           => throw WrongTypeException(path, "an object", typeName(other))

  /** The duration at `path`, written HOCON-style (`10s`, `5 minutes`, `500ms`); a bare number is read
    * as milliseconds. Returns a cross-platform [[scala.concurrent.duration.FiniteDuration]].
    */
  def getDuration(path: String): FiniteDuration =
    val s = stringForUnit(path, "a duration")
    Units.parseDuration(s).getOrElse(throw WrongTypeException(path, "a duration", s"string '$s'"))

  /** The size in bytes at `path`, written HOCON-style (`512K`, `10MB`, `1 GiB`); a bare number is a
    * byte count. Powers-of-1024 and powers-of-1000 units are distinguished per the spec.
    */
  def getBytes(path: String): Long =
    val s = stringForUnit(path, "a size")
    Units.parseBytes(s).getOrElse(throw WrongTypeException(path, "a size", s"string '$s'"))

  private def stringForUnit(path: String, expected: String): String = requirePath(path) match
    case ConfigString(s) => s
    case ConfigNumber(r) => r
    case other           => throw WrongTypeException(path, expected, typeName(other))

  def getValue(path: String): ConfigValue = requirePath(path)

  /** Decode this whole config into a typed value `A` — typically a case class whose field names match
    * the top-level keys. A `Decoder[A]` for any case class (and nested case classes) is derived
    * automatically; the primitive field types `String`, `Int`, `Long`, `Double`, `Boolean`,
    * `FiniteDuration`, `Option[_]`, `List[_]`, and `Map[String, _]` are supported out of the box.
    * Throws [[MissingPathException]] for an absent required field and [[WrongTypeException]] when a
    * value is the wrong shape; both carry the dotted field path for context.
    */
  def as[A](using d: Decoder[A]): A = d.decode(root, "")

  /** Decode the value at `path` into a typed value `A`, the same way [[as]] decodes the whole config.
    */
  def getAs[A](path: String)(using d: Decoder[A]): A = d.decode(requirePath(path), path)

  def getList(path: String): List[ConfigValue] = requirePath(path) match
    case ConfigArray(es) => es
    case other           => throw WrongTypeException(path, "a list", typeName(other))

  def getStringList(path: String): List[String] = getList(path).map {
    case ConfigString(s)  => s
    case ConfigNumber(r)  => r
    case ConfigBoolean(b) => b.toString
    case other            => throw WrongTypeException(path, "a string list", typeName(other))
  }

  def getStringOpt(path: String): Option[String]   = if hasPath(path) then Some(getString(path)) else None
  def getIntOpt(path: String): Option[Int]         = if hasPath(path) then Some(getInt(path)) else None
  def getLongOpt(path: String): Option[Long]       = if hasPath(path) then Some(getLong(path)) else None
  def getDoubleOpt(path: String): Option[Double]   = if hasPath(path) then Some(getDouble(path)) else None
  def getBooleanOpt(path: String): Option[Boolean] = if hasPath(path) then Some(getBoolean(path)) else None
  def getConfigOpt(path: String): Option[Config]   = if hasPath(path) then Some(getConfig(path)) else None
  def getListOpt(path: String): Option[List[ConfigValue]] =
    if hasPath(path) then Some(getList(path)) else None
  def getDurationOpt(path: String): Option[FiniteDuration] =
    if hasPath(path) then Some(getDuration(path)) else None
  def getBytesOpt(path: String): Option[Long] = if hasPath(path) then Some(getBytes(path)) else None

  /** This config overriding `fallback`: values here win, `fallback` fills in keys this config lacks.
    * Objects present in both merge recursively; arrays and scalars from this config replace.
    */
  def withFallback(fallback: Config): Config = Config(root.withFallback(fallback.root))

/** Entry point: parse and combine HOCON documents. */
object Hocon:

  /** Parse HOCON source and resolve its `${...}` substitutions. Substitutions resolve against this
    * document only; required ones that are found nowhere raise [[UnresolvedSubstitutionException]].
    * The pure default touches no IO, so `include` directives have no source: optional includes are
    * skipped and a `required(...)` include raises — pass a [[ConfigSource]] to load them.
    */
  def parse(input: String): Config = parse(input, EnvSource.empty, ConfigSource.empty)

  /** Parse and resolve, falling back to `env` for any substitution not found in the document. */
  def parse(input: String, env: EnvSource): Config = parse(input, env, ConfigSource.empty)

  /** Parse and resolve, loading `include` directives through `source`. */
  def parse(input: String, source: ConfigSource): Config = parse(input, EnvSource.empty, source)

  /** Parse and resolve with both seams supplied: `env` for substitution fallback and `source` for
    * `include` directives. The document's root must be an object (the usual case); parse a document
    * whose root is an array with [[parseValue]] instead.
    */
  def parse(input: String, env: EnvSource, source: ConfigSource): Config =
    parseValue(input, env, source) match
      case o: ConfigObject => Config(o)
      case other =>
        throw WrongTypeException("", "an object at the document root", ConfigValue.typeName(other))

  /** Parse and resolve a document, returning its root value directly. Per the HOCON spec the root may
    * be an object or an array, and this is the entry point that accepts either — use it when the input
    * is (or may be) a top-level array. For the common object-rooted document [[parse]] returns the
    * more convenient path-addressable [[Config]].
    */
  def parseValue(input: String): ConfigValue = parseValue(input, EnvSource.empty, ConfigSource.empty)

  /** Parse and resolve a document to its root value, falling back to `env` for substitutions. */
  def parseValue(input: String, env: EnvSource): ConfigValue =
    parseValue(input, env, ConfigSource.empty)

  /** Parse and resolve a document to its root value, loading `include` directives through `source`. */
  def parseValue(input: String, source: ConfigSource): ConfigValue =
    parseValue(input, EnvSource.empty, source)

  /** Parse and resolve a document to its root value with both seams supplied. */
  def parseValue(input: String, env: EnvSource, source: ConfigSource): ConfigValue =
    val root = Parser(Lexer(input).tokenize(), source).parseRoot()
    Resolver.resolve(root, env)

  /** Merge configs so that later arguments win over earlier ones — i.e. pass the base first and the
    * most specific overrides last. With no arguments this is the empty config.
    */
  def load(configs: Config*): Config =
    if configs.isEmpty then Config(ConfigObject.empty)
    else configs.reduceLeft((base, over) => over.withFallback(base))
