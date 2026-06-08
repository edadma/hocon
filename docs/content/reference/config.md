---
title: "Config"
weight: 1
---

`Config` is an immutable, untyped view over a parsed HOCON document. You obtain one from
`Hocon.parse`, navigate it by dot-separated path, and read typed values with the getters
below.

## Parsing and combining

These live on the `Hocon` object.

| Method | Returns | Description |
|--------|---------|-------------|
| `Hocon.parse(input: String)` | `Config` | Parse HOCON source text. Throws `ParseError` on a syntax error. |
| `Hocon.load(configs: Config*)` | `Config` | Merge configs so that **later arguments win**. No arguments → the empty config. |

```scala
import io.github.edadma.hocon.*

val config = Hocon.parse("""a = 1, b { c = 2 }""")
```

## Reading values

Paths are dot-separated (`a.b.c`). Each getter throws `MissingPathException` when the path is
absent or `null`, and `WrongTypeException` when the value is the wrong shape.

| Method | Returns | Notes |
|--------|---------|-------|
| `getString(path)` | `String` | Numbers and booleans are returned as their text. |
| `getInt(path)` | `Int` | Parses the numeric literal. |
| `getLong(path)` | `Long` | |
| `getDouble(path)` | `Double` | |
| `getBoolean(path)` | `Boolean` | Also accepts the strings `yes`/`no`/`on`/`off`. |
| `getConfig(path)` | `Config` | The sub-object at `path`. |
| `getList(path)` | `List[ConfigValue]` | The raw array elements. |
| `getStringList(path)` | `List[String]` | Each element coerced to string. |
| `getValue(path)` | `ConfigValue` | The raw value node, whatever its type. |

```scala
config.getInt("a")          // 1
config.getInt("b.c")        // 2
config.getConfig("b").getInt("c")  // 2
```

## Optional access

`hasPath` reports whether a path resolves to a present, non-null value. Each getter has an
`*Opt` variant that returns `None` instead of throwing on a missing path:

| Method | Returns |
|--------|---------|
| `hasPath(path)` | `Boolean` |
| `getStringOpt(path)` | `Option[String]` |
| `getIntOpt(path)` | `Option[Int]` |
| `getLongOpt(path)` | `Option[Long]` |
| `getDoubleOpt(path)` | `Option[Double]` |
| `getBooleanOpt(path)` | `Option[Boolean]` |
| `getConfigOpt(path)` | `Option[Config]` |
| `getListOpt(path)` | `Option[List[ConfigValue]]` |

```scala
config.hasPath("b.c")        // true
config.getStringOpt("nope")  // None
```

## Merging

| Method | Returns | Description |
|--------|---------|-------------|
| `withFallback(other: Config)` | `Config` | This config wins; `other` supplies defaults for keys this config lacks. Objects merge recursively; arrays and scalars replace. |

See the [merging guide](/guide/merging/) for the full semantics.

## The value tree

`getValue` and `getList` hand back `ConfigValue` nodes — the raw, untyped tree:

- `ConfigObject(fields: Map[String, ConfigValue])`
- `ConfigArray(elements: List[ConfigValue])`
- `ConfigString(value: String)`
- `ConfigNumber(raw: String)` — the original literal text, parsed on demand
- `ConfigBoolean(value: Boolean)`
- `ConfigNull`

`Config.root` exposes the top-level `ConfigObject` directly.

## Exceptions

All errors extend `HoconException`:

- `ParseError(message, line, col)` — a syntax error, with 1-based source position.
- `MissingPathException(path)` — nothing at the requested path (or it is `null`).
- `WrongTypeException(path, expected, found)` — the value is the wrong type for the getter.
