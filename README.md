# hocon

![Maven Central](https://img.shields.io/maven-central/v/io.github.edadma/hocon_sjs1_3)
[![Last Commit](https://img.shields.io/github/last-commit/edadma/hocon)](https://github.com/edadma/hocon/commits)
![GitHub](https://img.shields.io/github/license/edadma/hocon)
![Scala Version](https://img.shields.io/badge/Scala-3.8.4-blue.svg)
![ScalaJS Version](https://img.shields.io/badge/Scala.js-1.20.2-blue.svg)
![Scala Native Version](https://img.shields.io/badge/Scala_Native-0.5.10-blue.svg)

A pure-Scala, cross-platform implementation of [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md)
(Human-Optimized Config Object Notation) — the config format popularized by Lightbend's
`com.typesafe:config`.

Unlike `com.typesafe:config`, which is JVM-only, **hocon** is written in pure Scala 3 with no
`java.*` dependencies in its core, so the same parser runs on the **JVM**, in the browser/Node.js
via **Scala.js**, and as a native binary via **Scala Native**.

> **Status:** early development. The parser and untyped `Config` API are the first milestone;
> substitutions, includes, and a typed (case-class) decoder follow. See the roadmap below.

## Why

HOCON is a comfortable format for configuration and, in particular, for **internationalization
(i18n) message files** — nested, commented, human-friendly:

```hocon
# messages.en.conf
en {
  greeting = "Hello, world"
  nav { home = "Home", about = "About" }
  cart.items = "{count} items"      # path-expression key
}
```

Until now there has been no good way to read these on Scala.js or Scala Native. That's the gap
hocon fills.

## Roadmap

| Phase | Scope |
|------:|-------|
| **1** | Lexer + parser → untyped `Config` (comments, quoted/unquoted strings, nested objects, path-expression keys, arrays). **i18n-usable.** |
| **2** | Object merging + `withFallback` (base locale + overrides). |
| **3** | Substitutions: `${path}`, `${?path}`, env fallback, cycle detection. |
| **4** | Value concatenation + durations (`10s`) and sizes (`512K`, `10MB`). |
| **5** | `include` directives behind a pluggable, per-platform IO source. |
| **6** | Typed decoder with case-class derivation (`config.as[A]`). |
| **7** | Conformance against the Typesafe spec's test corpus. |

## Building and Testing

```bash
sbt hoconJVM/test
sbt hoconJS/test
sbt hoconNative/test
```

## License

ISC — see [LICENSE](LICENSE).
