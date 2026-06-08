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
