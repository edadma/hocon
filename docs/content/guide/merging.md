---
title: "Merging and fallback"
weight: 2
---

Real configuration is layered: a base with environment overrides, a default locale with
per-language translations, library defaults beneath application settings. hocon expresses
that with **fallback** — one config supplying defaults for another.

## withFallback

`a.withFallback(b)` returns a new config where `a` wins and `b` fills in whatever `a` does
not set:

```scala
val base     = Hocon.parse("a = 1, b = base")
val override_ = Hocon.parse("b = over, c = 3")

val merged = override_.withFallback(base)
merged.getInt("a")     // 1      — only in base
merged.getString("b")  // "over" — override wins
merged.getInt("c")     // 3      — only in override
```

## How merging works

The merge is recursive, and the rule is the same at every level:

- **Objects present on both sides merge recursively.** Their keys are unioned; shared keys
  follow these same rules one level down.
- **Scalars and arrays replace.** The winning side's value is taken whole — arrays are *not*
  concatenated or element-merged.
- **A `null` on the winning side shadows the fallback.** Because [`hasPath`](/reference/config/)
  treats `null` as absent, a `null` override effectively *unsets* a key the fallback defined.

```scala
val base = Hocon.parse("""
  server { host = localhost, port = 80, tags = [a, b] }
""")
val over = Hocon.parse("""
  server { port = 9000, tags = [c] }
""")

val merged = over.withFallback(base)
merged.getString("server.host")      // "localhost"  — kept from base
merged.getInt("server.port")         // 9000         — overridden
merged.getStringList("server.tags")  // List("c")    — arrays replace, not concat
```

## Layering several configs

`Hocon.load` merges any number of configs so that **later arguments win** — pass the base
first and the most specific overrides last:

```scala
val effective = Hocon.load(
  libraryDefaults,   // weakest
  applicationConfig,
  userOverrides,     // strongest
)
```

With no arguments it returns the empty config.

## Base locale with overrides

This is the i18n pattern: keep one complete base locale, and let each translation override
only the strings it changes. Anything a translation omits falls through to the base, so the
UI is never missing a string.

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

val messages = Messages(fr.withFallback(base))
messages("greeting")   // "Bonjour"   (translated)
messages("farewell")   // "Goodbye"   (from base)
messages("nav.home")   // "Accueil"   (translated)
messages("nav.about")  // "About"     (from base)
```
