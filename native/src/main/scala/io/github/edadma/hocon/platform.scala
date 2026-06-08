package io.github.edadma.hocon

val platform = "native"

/** The native environment, backed by `System.getenv`. */
private[hocon] def platformEnvSource: EnvSource = name => Option(System.getenv(name))

/** The native include source: file access through the cross-platform file API. Classpath and URL
  * kinds resolve to `None`.
  */
private[hocon] def platformConfigSource: ConfigSource = ConfigSource.files
