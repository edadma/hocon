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

object ConfigObject:
  val empty: ConfigObject = ConfigObject(ListMap.empty)

final case class ConfigArray(elements: List[ConfigValue]) extends ConfigValue

final case class ConfigString(value: String) extends ConfigValue

/** A numeric literal, stored as its original source text. Deferring the string→number conversion
  * to the getters keeps `8080` an int and `1.5` a double without committing to one runtime type.
  */
final case class ConfigNumber(raw: String) extends ConfigValue

final case class ConfigBoolean(value: Boolean) extends ConfigValue

case object ConfigNull extends ConfigValue
