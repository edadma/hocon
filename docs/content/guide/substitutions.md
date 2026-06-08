---
title: "Substitutions"
weight: 3
---

A substitution lets one value reference another, so you write a setting once and reuse it. hocon
resolves substitutions automatically when you call `Hocon.parse` — the config you get back has none
left in it.

## The two forms

`${path}` is a **required** substitution: it must resolve to something, or parsing fails.

```scala
val config = Hocon.parse("""
  host = localhost
  url  = ${host}
""")

config.getString("url")   // "localhost"
```

`${?path}` is **optional**: if it resolves to nothing, the field that holds it simply does not appear.

```scala
val config = Hocon.parse("""
  a = 1
  b = ${?not-set}
""")

config.hasPath("b")   // false
```

## Resolution rules

- **Substitutions resolve against the merged root**, so they are order-independent — a value may
  reference a key defined later in the document.
- **A path may point anywhere**, including into nested objects (`${server.host}`), and may copy a
  whole object or array, not just a scalar.
- **Chains resolve transitively** — `a = ${b}`, `b = ${c}`, `c = 1` makes `a` equal `1`.

```scala
val config = Hocon.parse("""
  defaults { timeout = 30, retries = 3 }
  service  = ${defaults}        # copies the whole object
  primary  = ${service.timeout} # 30
""")
```

## Environment fallback

A substitution that is not found in the config falls back to an [`EnvSource`](/reference/config/).
The default environment is empty (the core stays pure and platform-independent); pass one to
`Hocon.parse` to wire in the real environment or a fixed map:

```scala
val env = EnvSource.fromMap(Map("HOME" -> "/home/ada"))
val config = Hocon.parse("home = ${HOME}", env)
config.getString("home")   // "/home/ada"
```

Config values always win over the environment — the environment is only consulted when the path is
absent from the document.

[= note =]
Config values take precedence over the environment, and a required `${path}` found in neither raises
`UnresolvedSubstitutionException`. To make a substitution that may be absent, use the optional
`${?path}` form.
[= /note =]

## Cycles

A reference chain that closes on itself — including a value that references itself with no prior
definition — raises `CircularReferenceException`, with the chain in the message:

```scala
Hocon.parse("""
  a = ${b}
  b = ${a}
""")
// CircularReferenceException: Circular reference in substitution: a -> b -> a
```

## Concatenating with surrounding text

A substitution may also be one piece of a larger value. Written with whitespace next to other
text, it joins into a single string — this is [value concatenation](/guide/format/#value-concatenation):

```scala
val config = Hocon.parse("""
  host = example.com
  port = 8080
  url  = "http://"${host}":"${port}
""")

config.getString("url")   // "http://example.com:8080"
```

The pieces resolve and then concatenate, so a substitution can sit inside a URL, a path, or any
other composed string.
