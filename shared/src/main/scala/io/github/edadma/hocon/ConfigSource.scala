package io.github.edadma.hocon

/** How an `include` named its target. The bare `include "x"` form is [[Heuristic]] — the source
  * decides where to look; the qualified forms pin the lookup to a single mechanism.
  */
enum IncludeKind:
  case Heuristic, File, Url, Classpath

/** The capability the parser uses to resolve `include` directives into source text.
  *
  * Keeping IO behind this seam keeps the parser and resolver pure and identical on every platform —
  * the same include logic (merging, `required`, nesting, cycle detection) is exercised in the shared
  * test suite through [[ConfigSource.fromMap]], while real filesystem/classpath/URL access lives in a
  * thin per-platform [[ConfigSource.default]]. A `load` that returns `None` means "not found": an
  * optional include is then skipped, a `required` one raises [[IncludeException]].
  */
trait ConfigSource:
  def load(kind: IncludeKind, spec: String): Option[String]

object ConfigSource:
  /** A source that resolves nothing — the pure default, so `Hocon.parse(text)` never touches IO.
    * Optional includes are skipped; a `required` include raises.
    */
  val empty: ConfigSource = (_, _) => None

  /** An in-memory source keyed by the literal include spec, ignoring the [[IncludeKind]]. Handy for
    * virtual includes and for tests that need deterministic, platform-independent include content.
    */
  def fromMap(resources: Map[String, String]): ConfigSource = (_, spec) => resources.get(spec)

  /** The platform's real source: files (and, on the JVM, the classpath and URLs). On Scala.js and
    * Scala Native only file access is available; the other kinds resolve to `None`.
    */
  def default: ConfigSource = platformConfigSource
