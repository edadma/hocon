---
title: "The HOCON format"
weight: 1
---

HOCON is a superset of JSON: every JSON document is valid HOCON, but HOCON adds comments,
optional quoting, optional commas, and a handful of conveniences that make hand-written
config pleasant. This page covers the syntax hocon parses today.

## Objects and fields

The top-level braces are optional, and a field is a key, a separator (`=` or `:`), and a
value:

```hocon
name = "Roamer"
version : "1.0"
```

`=` and `:` are interchangeable. Fields are separated by a newline **or** a comma; a trailing
comma is allowed:

```hocon
a = 1, b = 2
c = 3
xs = [1, 2, 3,]
```

When a value is an object, the separator may be omitted entirely:

```hocon
window {
  width  = 1024
  height = 768
}
```

## Comments

Both `#` and `//` start a comment that runs to the end of the line:

```hocon
# a full-line comment
port = 8080   // a trailing comment
```

## Values

hocon recognizes the JSON value types plus unquoted strings:

```hocon
string  = "quoted text"
bare    = an-unquoted-string
number  = 8080
float   = 1.5
flag    = true
empty   = null
list    = [1, "two", true]
nested  = { a = 1, b = 2 }
```

`true`, `false`, and `null` are keywords; anything matching a number literal is a number;
everything else is a string.

### Quoting

[= note =]
HOCON allows unquoted strings, but **forbids** these characters inside them:
`$ " { } [ ] : = , + # ` ^ ? ! @ * & \`. Most natural-language strings hit one of them, so
quote your message strings. hocon raises a parse error pointing at the offending character
rather than guessing — this is spec-correct behavior.
[= /note =]

Bare words are convenient for identifiers, numeric values, and simple paths
(`localhost`, `recent-files`, `8080`). For anything with punctuation, quote it.

### String escapes

Quoted strings support the JSON escapes — `\"`, `\\`, `\/`, `\n`, `\t`, `\r`, `\b`, `\f`, and
`\uXXXX`:

```hocon
path    = "C:\\Users\\Ada"
caption = "line one\nline two"
heart   = "❤"
```

Triple-quoted strings are **raw** — no escapes are processed and newlines are kept verbatim,
which is handy for embedded text:

```hocon
banner = """
  Welcome to Roamer.
  Press ? for help.
"""
```

## Path-expression keys

A key may be a dotted path, which expands into nested objects. These two documents are
equivalent:

```hocon
a.b.c = value
```

```hocon
a { b { c = value } }
```

Keys that target the same object merge, so you can group related settings flatly:

```hocon
cart.items   = "{count} items"
cart.empty   = "Your cart is empty"
```

resolves to a `cart` object with both `items` and `empty`.

A key is a full path *expression*: each element may be quoted or unquoted, and only an
**unquoted** `.` separates elements — inside a quoted segment a dot is literal. An unquoted key
with interior whitespace is a single element (its edges trimmed), so `a b c` is one key, not
three:

```hocon
foo."bar.baz" = 1   # a two-element path; the second element is literally "bar.baz"
"a.b"         = 2   # a single key containing a dot
a b c         = 3   # the single key "a b c"
```

A key containing a literal dot is reachable through the parsed object structure but not through
the dotted-string getter API (`getString("a.b")`), which always splits on `.`.

## Arrays

Array elements are separated by commas or newlines, with an optional trailing comma, and may
be any value type — including objects and nested arrays:

```hocon
ports = [
  8080
  8081
  8082,
]

users = [
  { name = "Ada",  admin = true  },
  { name = "Alan", admin = false },
]
```

## Substitutions

`${path}` references another value, and `${?path}` is its optional form. They resolve against
the merged document, so they are order-independent:

```hocon
host = localhost
url  = ${host}        # → "localhost"
```

See the [substitutions guide](/guide/substitutions/) for the full rules — environment
fallback, object copying, and cycle detection.

## Value concatenation

Several pieces written on one line with only whitespace between them concatenate into a single
value. Strings, numbers, booleans, and substitutions join into one string, with the interior
whitespace preserved and the ends trimmed:

```hocon
host = example.com
port = 8080
url  = "http://"${host}":"${port}   # → "http://example.com:8080"
full = first   middle  last         # → "first   middle  last"
```

Arrays concatenate element-wise, and objects deep-merge left to right:

```hocon
xs   = [1, 2] [3, 4]                 # → [1, 2, 3, 4]
conf = ${defaults} { retries = 5 }   # the defaults object with retries overridden
```

Mixing kinds that cannot combine — an object or array joined with a string — raises a
`HoconConcatException`.

### Appending to arrays and self-reference

A field can refer to its own previous value, which HOCON resolves by looking *backward* to the
value already in scope rather than treating it as a cycle:

```hocon
path = [/bin]
path = ${path} [/usr/bin]   # → [/bin, /usr/bin]
```

The `+=` shorthand appends a single element to the array already at a key (or starts a fresh
array if the key is absent) — it is exactly `key = ${?key} [value]`:

```hocon
ports = [80]
ports += 443                # → [80, 443]
```

`+=` looks back across object blocks, so the prior value can come from an earlier
`server { … }` rather than the same one:

```hocon
server { ports = [80]  }
server { ports += 443 }      # → server.ports = [80, 443]
```

## Durations and sizes

A value can be read as a time duration or a memory size with the dedicated getters; in the
source it is just a number with a unit suffix (an optional space is allowed):

```hocon
timeout   = 10s
poll      = 500ms
linger    = 5 minutes
cache     = 512K
max-upload = 10MB
```

`getDuration` returns a cross-platform `FiniteDuration`; `getBytes` returns a `Long`. Duration
units are `ns`, `us`, `ms`, `s`, `m`, `h`, `d` (and their long spellings), with a bare number
read as milliseconds. Size units distinguish powers of 1024 (`K`, `Ki`, `KiB`, …) from powers of
1000 (`kB`, `MB`, …), with a bare number read as bytes. See the
[`Config` reference](/reference/config/).

## Includes

An `include` statement pulls another document in at that point in an object. The included
fields merge as if they had been written there, so later fields override them and substitutions
see the combined tree:

```hocon
include "defaults.conf"
host = override.example.com   # wins over anything defaults.conf set for host
```

The bare form lets the source decide where to look; the qualified forms pin it to one
mechanism, and `required(...)` turns a missing target into an error instead of a silent skip:

```hocon
include file("local.conf")
include classpath("reference.conf")
include url("https://example.com/shared.conf")
include required("must-exist.conf")
```

Where an include is read from goes through a [`ConfigSource`](/reference/config/) you pass to
`Hocon.parse`. The default source reads **files**, identically on every platform (it uses the
cross-platform file API). The `file(...)` and `required(...)` qualifiers are honoured by the
default; `url(...)` and `classpath(...)` are recognised but have no portable meaning, so the
default does not serve them — supply a `ConfigSource.fromMap` or your own `ConfigSource` to
resolve those. A missing optional include is ignored; a missing `required(...)` one raises
`IncludeException`, as does a cycle of files that include each other.

Because unquoted strings forbid `:` and `//` starts a comment, **URLs must be quoted**
(`url = "https://example.com"`) — this matches the reference implementation.
