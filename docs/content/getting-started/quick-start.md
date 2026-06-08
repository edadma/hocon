---
title: "Quick Start"
weight: 2
---

Parse a HOCON document, read typed values out of it, and use it for i18n messages.

## Parse and read

`Hocon.parse` turns a string of HOCON into an immutable [`Config`](/reference/config/). Read
values with typed getters; paths are dot-separated.

```scala
import io.github.edadma.hocon.*

val config = Hocon.parse("""
  app {
    name = "Roamer"
    window { width = 1024, height = 768 }
    recent-files = ["a.txt", "b.txt"]
    telemetry = false
  }
""")

config.getString("app.name")              // "Roamer"
config.getInt("app.window.width")         // 1024
config.getBoolean("app.telemetry")        // false
config.getStringList("app.recent-files")  // List("a.txt", "b.txt")

// Drill into a sub-tree:
val window = config.getConfig("app.window")
window.getInt("height")                   // 768
```

A missing path throws `MissingPathException`; a value of the wrong shape throws
`WrongTypeException`. When a key may be absent, use `hasPath` or the `*Opt` variants:

```scala
config.hasPath("app.theme")               // false
config.getStringOpt("app.theme")          // None
config.getIntOpt("app.window.width")      // Some(1024)
```

## A note on quoting

HOCON lets you write unquoted strings, but it **forbids** these characters inside them:

```
$ " { } [ ] : = , + # ` ^ ? ! @ * & \
```

Most real UI strings — `Hello, world`, `Are you sure?`, `{count} items` — contain one of
them, so **quote your translation strings**. This is spec-correct HOCON, not a limitation of
this library; bare words are fine for identifiers, numbers, and simple paths.

```hocon
name      = roamer            # fine — a bare identifier
greeting  = "Hello, world"    # must be quoted — contains a comma
```

## i18n messages

HOCON's nesting and comments make it a comfortable format for translation files. The
`Messages` helper looks a string up by path and fills `{name}` placeholders:

```scala
val en = Hocon.parse("""
  greeting   = "Hello, {name}"
  cart.items = "{count} items in your cart"
""")

val m = Messages(en)
m("greeting", "name" -> "Ada")   // "Hello, Ada"
m("cart.items", "count" -> 3)    // "3 items in your cart"
```

A placeholder with no matching argument is left untouched, so a missing value is visible
rather than silently dropped.

## Base locale with overrides

Keep one complete base locale and let each translation override only what it changes. Merge
with `withFallback`:

```scala
val base = Hocon.parse("""
  greeting = "Hello"
  farewell = "Goodbye"
  nav { home = "Home", about = "About" }
""")

val fr = Hocon.parse("""
  greeting = "Bonjour"
  nav { home = "Accueil" }
""")

val m = Messages(fr.withFallback(base))
m("greeting")   // "Bonjour"   (translated)
m("farewell")   // "Goodbye"   (from base)
m("nav.home")   // "Accueil"   (translated)
m("nav.about")  // "About"     (from base)
```

See the [merging guide](/guide/merging/) for the full rules, and the
[`Config` reference](/reference/config/) for every getter.
