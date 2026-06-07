package io.github.edadma.hocon

import scala.util.matching.Regex

/** A thin i18n helper over a [[Config]] of translation strings.
  *
  * Messages are looked up by dot-path and may contain `{name}` placeholders that are filled from the
  * supplied arguments. A placeholder with no matching argument is left untouched, so a missing value
  * is visible rather than silently dropped. This is a convenience layer, deliberately separate from
  * the parser — the config it reads is plain HOCON.
  */
final class Messages(config: Config):

  private val placeholder = "\\{([a-zA-Z0-9_]+)\\}".r

  /** Look up `path` and substitute `{name}` placeholders from `args`. */
  def apply(path: String, args: (String, Any)*): String =
    val template = config.getString(path)
    if args.isEmpty then template
    else
      val m = args.toMap
      placeholder.replaceAllIn(
        template,
        (mat: Regex.Match) =>
          val key = mat.group(1)
          Regex.quoteReplacement(m.get(key).map(_.toString).getOrElse(mat.matched)),
      )

  /** Look up `path` with no placeholder substitution. */
  def get(path: String): String = config.getString(path)

  def hasPath(path: String): Boolean = config.hasPath(path)
