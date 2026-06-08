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

## Not yet supported

`include` directives — pulling in other files — are on the [roadmap](/guide/roadmap/).

Because unquoted strings forbid `:` and `//` starts a comment, **URLs must be quoted**
(`url = "https://example.com"`) — this matches the reference implementation.
