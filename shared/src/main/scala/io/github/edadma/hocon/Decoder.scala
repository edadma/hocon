package io.github.edadma.hocon

import scala.collection.immutable.ListMap
import scala.compiletime.{constValueTuple, erasedValue, summonInline}
import scala.concurrent.duration.FiniteDuration
import scala.deriving.Mirror

/** A typed reader from the untyped [[ConfigValue]] tree into a value of type `A`.
  *
  * Decoders for the primitive types, `Option`, `List`, `Map[String, _]`, [[Config]], and the raw
  * [[ConfigValue]] are provided as givens; a decoder for any case class (and case classes nested
  * inside it) is derived automatically from its `Mirror`, mapping each field to the object key of the
  * same name. Decoding failures throw [[MissingPathException]] or [[WrongTypeException]], each
  * carrying the dotted path of the offending field so the message points at the right place.
  *
  * `path` threads that location through nested decoding and is `""` at the root; callers normally use
  * [[Config.as]] / [[Config.getAs]] rather than invoking `decode` directly.
  */
trait Decoder[A]:
  def decode(value: ConfigValue, path: String): A

  /** The value to use when this decoder's field is absent from the enclosing object. `None` (the
    * default) makes the field required; the `Option` decoder overrides it so a missing field is
    * decoded as `None` rather than raising.
    */
  def whenAbsent: Option[A] = None

object Decoder:

  /** Summon one whenever a `Decoder[A]` is needed but not in scope as a `given`. */
  def apply[A](using d: Decoder[A]): Decoder[A] = d

  private def numericText(value: ConfigValue, path: String): String = value match
    case ConfigNumber(r) => r
    case ConfigString(s) => s
    case other           => throw WrongTypeException(path, "a number", ConfigValue.typeName(other))

  private def number[A](ty: String)(f: String => A): Decoder[A] = (value, path) =>
    val text = numericText(value, path)
    try f(text)
    catch case _: NumberFormatException => throw WrongTypeException(path, ty, "string")

  given Decoder[String] = (value, path) =>
    value match
      case ConfigString(s)  => s
      case ConfigNumber(r)  => r
      case ConfigBoolean(b) => b.toString
      case other            => throw WrongTypeException(path, "a string", ConfigValue.typeName(other))

  given Decoder[Int]    = number("an int")(BigDecimal(_).toInt)
  given Decoder[Long]   = number("a long")(BigDecimal(_).toLong)
  given Decoder[Double] = number("a double")(_.toDouble)

  given Decoder[Boolean] = (value, path) =>
    value match
      case ConfigBoolean(b) => b
      case ConfigString(s) =>
        s.toLowerCase match
          case "true" | "yes" | "on"  => true
          case "false" | "no" | "off" => false
          case _                      => throw WrongTypeException(path, "a boolean", "string")
      case other => throw WrongTypeException(path, "a boolean", ConfigValue.typeName(other))

  /** A HOCON duration (`10s`, `5 minutes`); a bare number is read as milliseconds. */
  given Decoder[FiniteDuration] = (value, path) =>
    val text = value match
      case ConfigString(s) => s
      case ConfigNumber(r) => r
      case other           => throw WrongTypeException(path, "a duration", ConfigValue.typeName(other))
    Units.parseDuration(text).getOrElse(throw WrongTypeException(path, "a duration", s"string '$text'"))

  /** The raw, undecoded value — an escape hatch for a field that needs the untyped tree. */
  given Decoder[ConfigValue] = (value, _) => value

  given Decoder[Config] = (value, path) =>
    value match
      case o: ConfigObject => Config(o)
      case other           => throw WrongTypeException(path, "an object", ConfigValue.typeName(other))

  /** An optional field: a present `null` and an absent key both decode to `None`. */
  given option[A](using d: Decoder[A]): Decoder[Option[A]] with
    def decode(value: ConfigValue, path: String): Option[A] = value match
      case ConfigNull => None
      case other      => Some(d.decode(other, path))
    override def whenAbsent: Option[Option[A]] = Some(None)

  given list[A](using d: Decoder[A]): Decoder[List[A]] = (value, path) =>
    value match
      case ConfigArray(es) => es.zipWithIndex.map((e, i) => d.decode(e, s"$path[$i]"))
      case other           => throw WrongTypeException(path, "a list", ConfigValue.typeName(other))

  /** An object whose every value is decoded as `A`, keeping the source key order. */
  given map[A](using d: Decoder[A]): Decoder[Map[String, A]] = (value, path) =>
    value match
      case o: ConfigObject =>
        ListMap.from(o.fields.map { (k, v) =>
          k -> d.decode(v, if path.isEmpty then k else s"$path.$k")
        })
      case other => throw WrongTypeException(path, "an object", ConfigValue.typeName(other))

  private inline def summonAll[T <: Tuple]: List[Decoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[Decoder[h]] :: summonAll[t]

  /** Derive a decoder for any case class: each field is read from the object key of the same name,
    * recursing into nested case classes. Missing required fields and type mismatches throw with the
    * field's dotted path.
    */
  inline given derived[A](using m: Mirror.ProductOf[A]): Decoder[A] =
    val labels   = constValueTuple[m.MirroredElemLabels].toList.map(_.toString)
    val decoders = summonAll[m.MirroredElemTypes]
    new ProductDecoder[A](labels, decoders, m)

  /** The decoder produced by [[derived]]. Public only so the inlined `derived` body can refer to it;
    * construct it through `summon[Decoder[A]]` / [[Config.as]] rather than directly.
    */
  final class ProductDecoder[A](
      labels: List[String],
      decoders: List[Decoder[?]],
      m: Mirror.ProductOf[A],
  ) extends Decoder[A]:
    def decode(value: ConfigValue, path: String): A = value match
      case o: ConfigObject =>
        val values = labels.zip(decoders).map { (label, dec) =>
          val fieldPath = if path.isEmpty then label else s"$path.$label"
          o.fields.get(label) match
            case Some(v) => dec.asInstanceOf[Decoder[Any]].decode(v, fieldPath)
            case None    => dec.whenAbsent.getOrElse(throw MissingPathException(fieldPath))
        }
        m.fromProduct(Tuple.fromArray(values.toArray))
      case other => throw WrongTypeException(path, "an object", ConfigValue.typeName(other))
