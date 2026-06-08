package io.github.edadma.hocon

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

/** Parsers for HOCON's two unit-suffixed value forms: time durations (`10s`, `5 minutes`) and memory
  * sizes (`512K`, `10MB`). Both accept an optional space between the number and the unit, and both
  * are pure string→value functions so they run identically on every platform. Each returns `None`
  * when the text is not a well-formed quantity, leaving the typed-getter caller to raise the error.
  */
object Units:

  /** Split a unit-suffixed quantity into its numeric text and its unit, the shape both getters share:
    * optional surrounding whitespace, a `-?digits(.digits)?` number, optional whitespace, then a run
    * of letters. Written as a hand scan so the core needs no regex engine and behaves identically on
    * every platform. Returns `None` when the whole string is not that shape.
    */
  private def splitQuantity(s: String): Option[(String, String)] =
    val t = s.trim
    val n = t.length
    var i = 0
    val numStart = i
    if i < n && t.charAt(i) == '-' then i += 1
    def digits(): Boolean =
      val start = i
      while i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9' do i += 1
      i > start
    if !digits() then return None
    if i < n && t.charAt(i) == '.' then
      i += 1
      if !digits() then return None
    val num = t.substring(numStart, i)
    while i < n && (t.charAt(i) == ' ' || t.charAt(i) == '\t') do i += 1
    val unitStart = i
    while i < n && { val c = t.charAt(i); (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') } do i += 1
    val unit = t.substring(unitStart, i)
    if i == n then Some((num, unit)) else None

  /** Parse a HOCON duration into a [[scala.concurrent.duration.FiniteDuration]]. Units follow the
    * spec — `ns`, `us`, `ms`, `s`, `m`, `h`, `d` and their long spellings; a bare number with no unit
    * is read as milliseconds.
    */
  def parseDuration(s: String): Option[FiniteDuration] =
    splitQuantity(s).flatMap { (num, unit) =>
      durationNanosPerUnit(unit.toLowerCase).map { per =>
        FiniteDuration((BigDecimal(num) * per).toLong, NANOSECONDS)
      }
    }

  private def durationNanosPerUnit(u: String): Option[Long] = u match
    case "" | "ms" | "milli" | "millis" | "millisecond" | "milliseconds" => Some(1000000L)
    case "ns" | "nano" | "nanos" | "nanosecond" | "nanoseconds"          => Some(1L)
    case "us" | "micro" | "micros" | "microsecond" | "microseconds"      => Some(1000L)
    case "s" | "second" | "seconds"                                      => Some(1000000000L)
    case "m" | "minute" | "minutes"                                      => Some(60000000000L)
    case "h" | "hour" | "hours"                                          => Some(3600000000000L)
    case "d" | "day" | "days"                                            => Some(86400000000000L)
    case _                                                               => None

  /** Parse a HOCON memory size into a count of bytes. Powers-of-1024 units (`K`, `Ki`, `KiB`, …) and
    * powers-of-1000 units (`kB`, `MB`, …) are both recognized, following the spec's distinction; a
    * bare number, `B`, or `byte(s)` is bytes. A fractional quantity truncates toward zero.
    */
  def parseBytes(s: String): Option[Long] =
    splitQuantity(s).flatMap { (num, unit) =>
      bytesPerUnit(unit).map(per => (BigDecimal(num) * BigDecimal(per)).toLong)
    }

  private def bytesPerUnit(u: String): Option[BigInt] =
    val k2 = BigInt(1024)
    val k10 = BigInt(1000)
    u match
      case "" | "B" | "b" | "byte" | "bytes" => Some(BigInt(1))

      case "K" | "k" | "Ki" | "KiB" | "kibibyte" | "kibibytes" => Some(k2.pow(1))
      case "M" | "Mi" | "MiB" | "mebibyte" | "mebibytes"       => Some(k2.pow(2))
      case "G" | "Gi" | "GiB" | "gibibyte" | "gibibytes"       => Some(k2.pow(3))
      case "T" | "Ti" | "TiB" | "tebibyte" | "tebibytes"       => Some(k2.pow(4))
      case "P" | "Pi" | "PiB" | "pebibyte" | "pebibytes"       => Some(k2.pow(5))
      case "E" | "Ei" | "EiB" | "exbibyte" | "exbibytes"       => Some(k2.pow(6))

      case "kB" | "kilobyte" | "kilobytes" => Some(k10.pow(1))
      case "MB" | "megabyte" | "megabytes" => Some(k10.pow(2))
      case "GB" | "gigabyte" | "gigabytes" => Some(k10.pow(3))
      case "TB" | "terabyte" | "terabytes" => Some(k10.pow(4))
      case "PB" | "petabyte" | "petabytes" => Some(k10.pow(5))
      case "EB" | "exabyte" | "exabytes"   => Some(k10.pow(6))

      case _ => None
