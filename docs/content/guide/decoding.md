---
title: "Typed decoding"
weight: 4
---

The untyped `Config` getters are enough for reading a value here and there, but for a real
configuration it is nicer to map the whole document onto a case class and read typed fields
directly. `config.as[A]` does exactly that, deriving a decoder for `A` from its structure.

```scala
import io.github.edadma.hocon.*

case class Server(host: String, port: Int, debug: Boolean)

val config = Hocon.parse("""
  host  = localhost
  port  = 8080
  debug = true
""")

val server = config.as[Server]   // Server("localhost", 8080, true)
```

Each case-class field is read from the object key of the same name. No annotations, no
companion boilerplate, and — because derivation is pure compile-time `Mirror` work — it runs
identically on the JVM, Scala.js, and Scala Native.

## Nesting

A field whose type is itself a case class decodes from the nested object:

```scala
case class App(name: String, server: Server)

val config = Hocon.parse("""
  name = demo
  server { host = localhost, port = 9000, debug = false }
""")

config.as[App]   // App("demo", Server("localhost", 9000, false))
```

To decode a value that is not at the root, point `getAs` at a path:

```scala
config.getAs[Server]("server")
```

## Supported field types

Decoders for these are available out of the box, and compose — a `List[Server]` or
`Option[Map[String, Int]]` works because the element decoders do:

| Type | Read from |
|------|-----------|
| `String` | a string (numbers and booleans coerce to their text) |
| `Int`, `Long`, `Double` | a numeric literal |
| `Boolean` | `true`/`false` (also `yes`/`no`/`on`/`off`) |
| `FiniteDuration` | a HOCON duration (`10s`, `5 minutes`); a bare number is milliseconds |
| `Option[A]` | the value, or `None` when the key is absent or `null` |
| `List[A]` | an array, each element decoded as `A` |
| `Map[String, A]` | an object, each value decoded as `A` (key order preserved) |
| `Config` | the sub-object, left untyped |
| `ConfigValue` | the raw value node — an escape hatch |
| a nested case class | the nested object |

```scala
import scala.concurrent.duration.*

case class Service(
  name:    String,
  tags:    List[String],
  timeout: FiniteDuration,
  note:    Option[String],     // absent or null → None
)

Hocon.parse("""
  name    = web
  tags    = [http, public]
  timeout = 30s
""").as[Service]
// Service("web", List("http", "public"), 30.seconds, None)
```

An `Option` field is the way to make a setting optional: a missing key decodes to `None`
rather than failing. Every other field is required.

## Errors

Decoding reuses the same exceptions as the untyped getters, and each one carries the dotted
path of the field that failed — including through nested objects:

```scala
case class App(name: String, server: Server)

// server.port is not a number:
Hocon.parse("""name = demo, server { host = h, port = bad, debug = false }""").as[App]
// WrongTypeException: value at 'server.port' has type string, but a number was requested
```

- A required field that is absent throws `MissingPathException`, with the missing field's path.
- A field whose value is the wrong shape throws `WrongTypeException`, with that field's path.

See the [`Config` reference](/reference/config/#typed-decoding) for the `as` / `getAs`
signatures.
