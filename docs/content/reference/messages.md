---
title: "Messages"
weight: 2
---

`Messages` is a thin i18n helper over a [`Config`](/reference/config/) of translation
strings. It looks messages up by path and fills `{name}` placeholders from named arguments.
It is a convenience layer — the config it reads is ordinary HOCON.

## Constructing

Wrap any `Config` whose values are the message strings:

```scala
import io.github.edadma.hocon.*

val en = Hocon.parse("""
  greeting   = "Hello, {name}"
  cart.items = "{count} items in your cart"
  plain      = "No placeholders here"
""")

val m = Messages(en)
```

For a base locale with per-language overrides, merge first with
[`withFallback`](/guide/merging/) and wrap the result:

```scala
val m = Messages(fr.withFallback(base))
```

## Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `apply(path, args: (String, Any)*)` | `String` | Look up `path` and substitute `{name}` placeholders from `args`. |
| `get(path)` | `String` | Look up `path` with no substitution. |
| `hasPath(path)` | `Boolean` | Whether the underlying config has a value at `path`. |

```scala
m("greeting", "name" -> "Ada")   // "Hello, Ada"
m("cart.items", "count" -> 3)    // "3 items in your cart"
m("plain")                       // "No placeholders here"
```

## Behavior

- Placeholders are `{name}` where the name is letters, digits, or underscores.
- Argument values are converted with `toString`, so numbers, booleans, and any type work.
- A placeholder with **no matching argument is left untouched**, so a missing value shows up
  in the output rather than disappearing silently:

```scala
m("greeting", "other" -> 1)      // "Hello, {name}"
```

- A missing message path throws `MissingPathException`, like any other `Config` lookup.
