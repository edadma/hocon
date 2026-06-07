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
