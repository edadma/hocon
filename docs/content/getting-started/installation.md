---
title: "Installation"
weight: 1
---

hocon is a pure-Scala library with no native dependencies, so there is nothing to install at
the system level — just add it to your build.

[= note =]
Every [roadmap](/guide/roadmap/) phase is complete: the parser, the untyped `Config` API, object
merging, substitutions, value concatenation, durations/sizes, `include` directives, the
`config.as[A]` typed decoder, and HOCON-spec conformance (path expressions, `+=` append,
self-referential substitutions) are all in place and tested identically on the JVM, Scala.js, and
Scala Native.
[= /note =]

## Requirements

- Scala 3
- sbt
- For Scala.js / Scala Native targets: the usual `sbt-scalajs` / `sbt-scala-native` plugins

No `java.*` dependency is used in the core, and there are no native libraries to link.

## Add the dependency

hocon cross-publishes for the JVM, Scala.js, and Scala Native. Use the `%%%` operator so sbt
picks the right artifact for each platform:

```scala
libraryDependencies += "io.github.edadma" %%% "hocon" % "0.1.0"
```

In a `crossProject` build the single line covers every target:

```scala
lazy val app = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .settings(
    libraryDependencies += "io.github.edadma" %%% "hocon" % "0.1.0",
  )
```

## From source

To track the latest changes, you can depend on hocon as a source dependency instead. Clone it
next to your project:

```bash
git clone https://github.com/edadma/hocon.git
```

and reference the cross-built module by relative path in your `build.sbt`:

```scala
dependsOn(ProjectRef(file("../hocon"), "hocon"))
```

## Verify the setup

hocon's own suite is pure Scala and runs headlessly on every platform:

```bash
sbt hoconJVM/test
sbt hoconJS/test
sbt hoconNative/test
```

All three run the identical tests — that cross-platform parity is the whole point of the
library. Continue to the [quick start](/getting-started/quick-start/) to parse a config.
