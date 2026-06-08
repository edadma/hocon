package io.github.edadma.hocon

val platform = "native"

/** The native environment, backed by `System.getenv`. */
private[hocon] def platformEnvSource: EnvSource = name => Option(System.getenv(name))
