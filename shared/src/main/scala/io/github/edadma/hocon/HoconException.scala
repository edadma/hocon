package io.github.edadma.hocon

/** Base type for every error this library raises, so callers can catch the whole family at once. */
sealed class HoconException(message: String) extends RuntimeException(message)

/** A syntax error in the source text, carrying the 1-based line and column where it was detected. */
final class ParseError(message: String, val line: Int, val col: Int)
    extends HoconException(s"$message (line $line, column $col)")

/** Thrown when a requested path is absent (or explicitly set to `null`). */
final class MissingPathException(val path: String)
    extends HoconException(s"No configuration setting found for path '$path'")

/** Thrown when a value exists at a path but is not of the type the caller asked for. */
final class WrongTypeException(val path: String, expected: String, found: String)
    extends HoconException(s"Configuration value at '$path' has type $found, but $expected was requested")

/** Thrown when a required `${path}` substitution resolves to nothing — not in the config and not in
  * the environment. Optional `${?path}` substitutions never raise this; they simply disappear.
  */
final class UnresolvedSubstitutionException(val path: String)
    extends HoconException(s"Could not resolve substitution: $${$path}")

/** Thrown when substitutions reference each other in a cycle (including a value referencing itself).
  * The message traces the chain that closed the loop.
  */
final class CircularReferenceException(val chain: List[String])
    extends HoconException(s"Circular reference in substitution: ${chain.mkString(" -> ")}")

/** Thrown when a value concatenation mixes incompatible kinds — an object or array joined with a
  * string, which HOCON does not allow.
  */
final class HoconConcatException(message: String) extends HoconException(message)

/** Thrown when an `include` cannot be honoured: a `required(...)` target that no source resolves, or
  * a cycle of files that include one another.
  */
final class IncludeException(message: String) extends HoconException(message)
