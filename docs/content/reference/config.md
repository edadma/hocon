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
| `Hocon.parse(input: String)` | `Config` | Parse HOCON source and resolve `${...}` substitutions. Throws `ParseError` on a syntax error. |
| `Hocon.parse(input: String, env: EnvSource)` | `Config` | As above, falling back to `env` for substitutions absent from the document. |
| `Hocon.parse(input: String, source: ConfigSource)` | `Config` | As above, loading `include` directives through `source`. |
| `Hocon.parse(input: String, env: EnvSource, source: ConfigSource)` | `Config` | Both seams supplied. |
| `Hocon.load(configs: Config*)` | `Config` | Merge configs so that **later arguments win**. No arguments → the empty config. |

`EnvSource` is the seam substitutions use for environment fallback. The default is
`EnvSource.empty`; build one from a map with `EnvSource.fromMap(...)`, use `EnvSource.system`
for the real process environment, or implement the single-method trait yourself. See the
[substitutions guide](/guide/substitutions/).

`ConfigSource` is the seam `include` directives use to read other documents. The default is
`ConfigSource.empty` (no IO — optional includes are skipped, a `required(...)` one raises);
`ConfigSource.fromMap(...)` resolves includes from an in-memory map, and `ConfigSource.default`
reads the filesystem on every platform plus the classpath and URLs on the JVM. See the
[format guide](/guide/format/#includes).

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
| `getDuration(path)` | `FiniteDuration` | A HOCON duration (`10s`, `5 minutes`); a bare number is milliseconds. |
| `getBytes(path)` | `Long` | A HOCON size in bytes (`512K`, `10MB`); powers of 1024 and 1000 are distinguished. |
| `getValue(path)` | `ConfigValue` | The raw value node, whatever its type. |

```scala
config.getInt("a")          // 1
config.getInt("b.c")        // 2
config.getConfig("b").getInt("c")  // 2
```

### Durations and sizes

`getDuration` reads a HOCON time value into a cross-platform
`scala.concurrent.duration.FiniteDuration`. Units are `ns`, `us`, `ms`, `s`, `m`, `h`, `d` and
their long spellings; a number with no unit is read as milliseconds.

`getBytes` reads a HOCON memory size into a `Long` count of bytes. Powers-of-1024 units (`K`,
`Ki`, `KiB`, …) and powers-of-1000 units (`kB`, `MB`, …) are distinguished per the spec; a bare
number is a byte count, and a fractional quantity truncates.

```scala
val c = Hocon.parse("timeout = 30s, cache = 512K")
c.getDuration("timeout")    // 30.seconds
c.getBytes("cache")         // 524288
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
| `getDurationOpt(path)` | `Option[FiniteDuration]` |
| `getBytesOpt(path)` | `Option[Long]` |

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
- `UnresolvedSubstitutionException(path)` — a required `${path}` found in neither the config
  nor the environment.
- `CircularReferenceException(chain)` — substitutions reference each other in a cycle.
- `HoconConcatException(message)` — a value concatenation mixes incompatible kinds, such as an
  object or array joined with a string.
- `IncludeException(message)` — a `required(...)` include resolved to nothing, or a cycle of
  files includes one another.
