package io.github.edadma.hocon

/** The capability a resolver uses to fall back to the environment when a `${path}` substitution is
  * not found in the config itself.
  *
  * Keeping it an injectable seam keeps the core pure and testable on every platform — the parser
  * and resolver never touch the real environment. The default is [[EnvSource.empty]] (no
  * environment); pass a custom one to `Hocon.parse` to wire in `System.getenv` or a test stub. A
  * platform-default implementation backed by the real environment arrives with the broader IO seam.
  */
trait EnvSource:
  def get(name: String): Option[String]

object EnvSource:
  /** An environment that never resolves anything. */
  val empty: EnvSource = (_: String) => None

  /** Build an `EnvSource` from a plain map — handy for tests and fixed environments. */
  def fromMap(vars: Map[String, String]): EnvSource = vars.get(_)

  /** The platform's real process environment (`System.getenv` on the JVM and Native, `process.env`
    * on Node). Pass it to `Hocon.parse` to let `${VAR}` substitutions fall back to the environment.
    */
  def system: EnvSource = platformEnvSource
