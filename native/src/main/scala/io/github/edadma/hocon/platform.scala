package io.github.edadma.hocon

import scala.util.Try

val platform = "native"

/** The native environment, backed by `System.getenv`. */
private[hocon] def platformEnvSource: EnvSource = name => Option(System.getenv(name))

/** The native include source. Only file access is available; classpath and URL kinds resolve to
  * `None`.
  */
private[hocon] def platformConfigSource: ConfigSource = NativeConfigSource

private object NativeConfigSource extends ConfigSource:
  def load(kind: IncludeKind, spec: String): Option[String] = kind match
    case IncludeKind.File | IncludeKind.Heuristic => readFile(spec)
    case _                                        => None

  private def readFile(path: String): Option[String] =
    Try {
      val src = scala.io.Source.fromFile(path)
      try src.mkString
      finally src.close()
    }.toOption
