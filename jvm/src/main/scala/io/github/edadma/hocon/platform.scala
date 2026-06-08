package io.github.edadma.hocon

val platform = "jvm"

/** The JVM environment, backed by `System.getenv`. */
private[hocon] def platformEnvSource: EnvSource = name => Option(System.getenv(name))
