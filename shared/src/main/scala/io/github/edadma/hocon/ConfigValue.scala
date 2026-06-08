package io.github.edadma.hocon

import scala.collection.immutable.ListMap

/** The untyped HOCON value tree produced by the parser.
  *
  * A `ConfigValue` mirrors JSON's data model (objects, arrays, strings, numbers, booleans, null),
  * which HOCON is a superset of. Numbers keep their original literal text so that integer-ness and
  * precision survive parsing — the typed getters on [[Config]] interpret the literal on demand.
  */
sealed trait ConfigValue

/** An object: an ordered map from key to value. Order is preserved (insertion order) so that
  * rendering and tests are deterministic; lookups are by key regardless of order.
  */
final case class ConfigObject(fields: Map[String, ConfigValue]) extends ConfigValue:
  def get(key: String): Option[ConfigValue] = fields.get(key)

  /** This object overriding `fallback`: `this` wins, `fallback` supplies defaults for keys this
    * object lacks. Objects present on both sides merge recursively.
    */
  def withFallback(fallback: ConfigObject): ConfigObject = ConfigObject.deepMerge(fallback, this)

object ConfigObject:
  val empty: ConfigObject = ConfigObject(ListMap.empty)

  /** Deep-merge two objects with `over` winning. Keys present on both sides merge recursively when
    * both values are objects; otherwise `over`'s value replaces — arrays and scalars never combine.
    * A `null` in `over` replaces as well, which (because [[Config.hasPath]] treats null as absent)
    * shadows, effectively unsetting, the fallback's value at that key.
    */
  def deepMerge(base: ConfigObject, over: ConfigObject): ConfigObject =
    var result = base.fields
    for (k, v) <- over.fields do
      result = (result.get(k), v) match
        case (Some(o: ConfigObject), n: ConfigObject) => result.updated(k, deepMerge(o, n))
        case _                                        => result.updated(k, v)
    ConfigObject(result)

final case class ConfigArray(elements: List[ConfigValue]) extends ConfigValue

final case class ConfigString(value: String) extends ConfigValue

/** A numeric literal, stored as its original source text. Deferring the string→number conversion
  * to the getters keeps `8080` an int and `1.5` a double without committing to one runtime type.
  */
final case class ConfigNumber(raw: String) extends ConfigValue

final case class ConfigBoolean(value: Boolean) extends ConfigValue

case object ConfigNull extends ConfigValue

/** An unresolved `${path}` reference. These exist only in the tree between parsing and resolution —
  * the resolver replaces every one, so a value tree returned from `Hocon.parse` never contains one.
  * `optional` marks the `${?path}` form, which disappears instead of erroring when nothing is found.
  */
final case class ConfigSubstitution(path: String, optional: Boolean) extends ConfigValue

/** An unresolved value concatenation — a whitespace-separated run of pieces written without a
  * separator, like `a "b" ${c}` or `[1] [2]` or `{x=1} {y=2}`. The resolver collapses it: a run of
  * arrays concatenates element-wise, a run of objects deep-merges left to right, and anything else
  * renders each piece to text and joins it, preserving the interior whitespace carried in `parts`.
  * Like [[ConfigSubstitution]], these never survive resolution.
  */
final case class ConfigConcat(parts: List[ConfigValue]) extends ConfigValue

/** Interior whitespace inside a [[ConfigConcat]]: it is preserved when the concatenation renders as a
  * string and ignored when it renders as an array or object. Appears only inside a concat's parts.
  */
private[hocon] final case class ConfigWhitespace(ws: String) extends ConfigValue

/** Internal sentinel for an optional substitution that resolved to nothing: the resolver drops the
  * field or array element that holds it. Never escapes resolution.
  */
private[hocon] case object ResolveMissing extends ConfigValue
