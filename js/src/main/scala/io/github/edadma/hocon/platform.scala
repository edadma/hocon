package io.github.edadma.hocon

import scala.scalajs.js
import scala.util.Try

val platform = "js"

/** The Node environment, backed by `process.env`. Returns `None` when there is no `process` (e.g. in
  * a browser) or the variable is unset.
  */
private[hocon] def platformEnvSource: EnvSource = name =>
  Try {
    val v = js.Dynamic.global.process.env.selectDynamic(name)
    if js.isUndefined(v) || v == null then None else Some(v.asInstanceOf[String])
  }.toOption.flatten

/** The Node include source. Only file access (via `fs.readFileSync`) is available; classpath and URL
  * kinds resolve to `None`.
  */
private[hocon] def platformConfigSource: ConfigSource = JsConfigSource

private object JsConfigSource extends ConfigSource:
  def load(kind: IncludeKind, spec: String): Option[String] = kind match
    case IncludeKind.File | IncludeKind.Heuristic => readFile(spec)
    case _                                        => None

  private def readFile(path: String): Option[String] =
    Try(js.Dynamic.global.require("fs").readFileSync(path, "utf8").asInstanceOf[String]).toOption
