package io.github.edadma.hocon

import scala.util.Try

val platform = "jvm"

/** The JVM environment, backed by `System.getenv`. */
private[hocon] def platformEnvSource: EnvSource = name => Option(System.getenv(name))

/** The JVM include source: files (through the cross-platform file API), plus the two JVM-only
  * mechanisms — classpath resources and URLs.
  */
private[hocon] def platformConfigSource: ConfigSource = JvmConfigSource

private object JvmConfigSource extends ConfigSource:
  def load(kind: IncludeKind, spec: String): Option[String] = kind match
    case IncludeKind.File      => ConfigSource.files.load(IncludeKind.File, spec)
    case IncludeKind.Classpath => readClasspath(spec)
    case IncludeKind.Url       => readUrl(spec)
    case IncludeKind.Heuristic =>
      ConfigSource.files.load(IncludeKind.File, spec).orElse(readClasspath(spec)).orElse(readUrl(spec))

  private def readClasspath(resource: String): Option[String] =
    Option(getClass.getClassLoader.getResourceAsStream(resource)).map { in =>
      try new String(in.readAllBytes(), "UTF-8")
      finally in.close()
    }

  private def readUrl(spec: String): Option[String] =
    Try {
      val in = java.net.URI.create(spec).toURL.openStream()
      try new String(in.readAllBytes(), "UTF-8")
      finally in.close()
    }.toOption
