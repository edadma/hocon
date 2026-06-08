---
title: hocon
splash: true
heroTitle: HOCON configuration for
heroHighlight: every Scala platform
summary: Read HOCON config and i18n files the same way on the JVM, in the browser via Scala.js, and as a native binary via Scala Native. hocon is a pure-Scala parser — no java.io, no classpath, no java.util.regex — so the format from Lightbend's typesafe config finally works everywhere Scala runs.
---

## What this is

**hocon** is a pure-Scala implementation of [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md)
(Human-Optimized Config Object Notation) — the comfortable, JSON-superset config format
popularized by Lightbend's `com.typesafe:config`.

The reference implementation is excellent, but it is **JVM-only**: it reaches for
`java.io`, the classpath, `java.util.regex`, and `java.time`. That leaves Scala.js and
Scala Native with no good way to read a `.conf` file. hocon fills that gap — the parser and
the untyped `Config` API are written in plain Scala 3 and live in a shared source set, so
the **same code runs on all three platforms**.

```scala
import io.github.edadma.hocon.*

val config = Hocon.parse("""
  en {
    greeting = "Hello, world"
    nav { home = "Home", about = "About" }
    cart.items = "{count} items"   # path-expression key
  }
""")

config.getString("en.greeting")    // "Hello, world"
config.getString("en.nav.home")    // "Home"
config.hasPath("en.missing")       // false
```

## Why HOCON for i18n

HOCON is a natural fit for **internationalization message files**: nested, commented,
human-friendly, and far less noisy than JSON. hocon ships a small `Messages` helper for the
`{name}` placeholder pattern, and Phase 2's merging makes a **base locale with per-locale
overrides** a one-liner.

```scala
val base = Hocon.parse("""greeting = "Hello", farewell = "Goodbye"""")
val fr   = Hocon.parse("""greeting = "Bonjour"""")

val messages = Messages(fr.withFallback(base))
messages("greeting")   // "Bonjour"  (translated)
messages("farewell")   // "Goodbye"  (from the base locale)
```

## What you get today

- **A full lexer and parser** — comments (`#` and `//`), quoted strings with escapes,
  triple-quoted multi-line strings, numbers, booleans, null, nested objects, arrays, and
  **path-expression keys** (`a.b.c = v`).
- **An untyped `Config` API** — typed getters (`getString`, `getInt`, `getBoolean`,
  `getConfig`, `getList`, …) with `*Opt` variants, `hasPath`, and clear exceptions.
- **Object merging and fallback** — `withFallback` and `Hocon.load`, with recursive object
  merge and last-wins layering.
- **Substitutions** — `${path}` and optional `${?path}`, resolved against the merged root,
  with environment fallback and cycle detection.
- **Value concatenation** — joining strings, arrays, and objects written side by side
  (`"http://"${host}`, `[1] [2]`, `${defaults} { … }`).
- **Durations and sizes** — `getDuration` (`10s`, `5 minutes`) and `getBytes` (`512K`, `10MB`).
- **Includes** — `include "other.conf"` (and `file`/`url`/`classpath`/`required` forms) behind a
  pluggable, per-platform `ConfigSource`.
- **An i18n sidecar** — `Messages` with placeholder interpolation.

A typed case-class decoder (`config.as[A]`) is on the [roadmap](/guide/roadmap/).

## Where to go next

- **[Getting Started](/getting-started/)** — add the dependency and parse your first config.
- **[Guide](/guide/)** — the HOCON format hocon supports, merging and fallback, and the roadmap.
- **[Reference](/reference/)** — the `Config` API and the `Messages` helper.
