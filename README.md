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

> **Status:** Phases 1–6 complete — the lexer, parser, untyped `Config` API, object merging,
> substitutions, value concatenation, durations/sizes, `include` directives, and a typed
> (case-class) decoder are in and tested on all three platforms. Only conformance against the
> reference test corpus remains. See the roadmap below.

## Usage

```scala
import io.github.edadma.hocon.*

val config = Hocon.parse("""
  en {
    greeting = "Hello, world"
    nav { home = "Home", about = "About" }
    cart.items = "{count} items"   # path-expression key
  }
""")

config.getString("en.greeting")   // "Hello, world"
config.getString("en.nav.home")   // "Home"
config.getConfig("en").getInt("...")
config.hasPath("en.missing")      // false

// i18n placeholder helper
val msg = Messages(config.getConfig("en"))
msg("cart.items", "count" -> 3)   // "3 items"
```

### Merging and fallback

A partial config falls back to a base — perfect for a base locale with per-locale overrides:

```scala
val base = Hocon.parse("""greeting = "Hello", farewell = "Goodbye"""")
val fr   = Hocon.parse("""greeting = "Bonjour"""")

val messages = Messages(fr.withFallback(base))
messages("greeting")   // "Bonjour"  (translated)
messages("farewell")   // "Goodbye"  (from base)

// Merge several layers; later arguments win:
val effective = Hocon.load(base, fr, userOverrides)
```

Objects merge recursively; arrays and scalars replace. A `null` in the override shadows (unsets)
the fallback value at that key.

### Substitutions

`${path}` references another value in the same document; `${?path}` is optional and disappears when
nothing is found. Substitutions resolve against the merged root, so they are order-independent.

```scala
val config = Hocon.parse("""
  host    = localhost
  url     = ${host}              # → "localhost"
  service = ${defaults}          # copies the whole object
  defaults { timeout = 30 }
""")

// A missing required substitution throws; an optional one is simply absent:
Hocon.parse("x = ${?missing}").hasPath("x")   // false

// Fall back to the environment for anything not in the config:
Hocon.parse("home = ${HOME}", EnvSource.fromMap(sys.env))
```

Circular references throw `CircularReferenceException`.

### Value concatenation

Pieces written side by side with only whitespace between them concatenate. Strings, numbers, and
substitutions join into one string (interior whitespace preserved); arrays concatenate element-wise;
objects deep-merge left to right.

```scala
val config = Hocon.parse("""
  host = example.com
  port = 8080
  url  = "http://"${host}":"${port}    // → "http://example.com:8080"
  xs   = [1, 2] [3, 4]                 // → [1, 2, 3, 4]
""")
```

### Durations and sizes

Read unit-suffixed values with `getDuration` (a cross-platform `FiniteDuration`) and `getBytes`
(a `Long`):

```scala
val config = Hocon.parse("timeout = 10s, cache = 512K")
config.getDuration("timeout")   // 10.seconds
config.getBytes("cache")        // 524288
```

Duration units are `ns`/`us`/`ms`/`s`/`m`/`h`/`d` (bare number = milliseconds); size units
distinguish powers of 1024 (`K`, `Ki`, `KiB`) from powers of 1000 (`kB`, `MB`).

### Includes

`include "other.conf"` pulls another document in at that point, merging its fields so later fields
override them. The qualified forms pin the lookup, and `required(...)` errors instead of skipping a
missing target. Where includes are read from is the one platform-specific corner — it goes through a
`ConfigSource` you pass to `parse`:

```scala
import io.github.edadma.hocon.*

// Read files (and, on the JVM, the classpath and URLs) with the platform default source:
val config = Hocon.parse("""include "app.conf"""", ConfigSource.default)

// Or resolve includes from memory — identical on every platform, ideal for tests:
val src = ConfigSource.fromMap(Map("app.conf" -> """name = "demo""""))
Hocon.parse("""include "app.conf"""", src).getString("name")   // "demo"
```

`ConfigSource.empty` (the default for `Hocon.parse(text)`) does no IO: optional includes are skipped
and a `required(...)` one raises `IncludeException`. Pass `EnvSource.system` alongside to let
`${VAR}` substitutions fall back to the real process environment.

A note on quoting: HOCON allows unquoted strings, but forbids the characters
`$ " { } [ ] : = , + # ` ^ ? ! @ * &` and `\` inside them. Most UI strings (`Hello, world`,
`Are you sure?`, `{count} items`) hit one of these, so **quote your translation strings**. This is
spec-correct HOCON, not a limitation of this library.

### Typed decoding

Map a whole document onto a case class with `config.as[A]`. A decoder is derived from the case
class at compile time (pure `Mirror` work, so it runs the same on all three platforms), reading
each field from the object key of the same name and recursing into nested case classes:

```scala
import io.github.edadma.hocon.*
import scala.concurrent.duration.*

case class Server(host: String, port: Int, debug: Boolean)
case class App(name: String, server: Server, tags: List[String], timeout: FiniteDuration)

val config = Hocon.parse("""
  name = demo
  server { host = localhost, port = 8080, debug = true }
  tags    = [http, public]
  timeout = 30s
""")

config.as[App]
// App("demo", Server("localhost", 8080, true), List("http", "public"), 30.seconds)
```

`String`, `Int`/`Long`/`Double`, `Boolean`, `FiniteDuration`, `Option` (absent or `null` →
`None`), `List`, `Map[String, _]`, `Config`, and nested case classes are supported. Failures
throw `MissingPathException` / `WrongTypeException` carrying the dotted field path. Use
`config.getAs[A](path)` to decode a value that isn't at the root.

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

| Phase | Scope | Status |
|------:|-------|:------:|
| **1** | Lexer + parser → untyped `Config` (comments, quoted/unquoted strings, nested objects, path-expression keys, arrays). **i18n-usable.** | ✅ |
| **2** | Object merging + `withFallback` (base locale + overrides). | ✅ |
| **3** | Substitutions: `${path}`, `${?path}`, env fallback, cycle detection. | ✅ |
| **4** | Value concatenation + durations (`10s`) and sizes (`512K`, `10MB`). | ✅ |
| **5** | `include` directives behind a pluggable, per-platform IO source. | ✅ |
| **6** | Typed decoder with case-class derivation (`config.as[A]`). | ✅ |
| **7** | Conformance against the Typesafe spec's test corpus. | |

## Building and Testing

```bash
sbt hoconJVM/test
sbt hoconJS/test
sbt hoconNative/test
```

## License

ISC — see [LICENSE](LICENSE).
