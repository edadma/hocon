package io.github.edadma.hocon

/** A thin i18n helper over a [[Config]] of translation strings.
  *
  * Messages are looked up by dot-path and may contain `{name}` placeholders that are filled from the
  * supplied arguments. A placeholder with no matching argument is left untouched, so a missing value
  * is visible rather than silently dropped. This is a convenience layer, deliberately separate from
  * the parser — the config it reads is plain HOCON.
  */
final class Messages(config: Config):

  /** Look up `path` and substitute `{name}` placeholders from `args`. */
  def apply(path: String, args: (String, Any)*): String =
    val template = config.getString(path)
    if args.isEmpty then template else interpolate(template, args.toMap)

  /** Replace each `{name}` whose `name` is a non-empty run of `[a-zA-Z0-9_]` with the matching
    * argument, leaving anything else — a `{` with no close, an unknown name, an empty `{}` — exactly
    * as written. Hand-scanned so the helper needs no regex engine on any platform.
    */
  private def interpolate(template: String, args: Map[String, Any]): String =
    val out = StringBuilder()
    val n   = template.length
    var i   = 0
    def isNameChar(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'
    while i < n do
      val c = template.charAt(i)
      if c == '{' then
        var j = i + 1
        while j < n && isNameChar(template.charAt(j)) do j += 1
        if j > i + 1 && j < n && template.charAt(j) == '}' then
          val key = template.substring(i + 1, j)
          args.get(key) match
            case Some(v) => out ++= v.toString; i = j + 1
            case None    => out += c; i += 1 // unknown placeholder left intact
        else
          out += c; i += 1
      else
        out += c; i += 1
    out.toString

  /** Look up `path` with no placeholder substitution. */
  def get(path: String): String = config.getString(path)

  def hasPath(path: String): Boolean = config.hasPath(path)
