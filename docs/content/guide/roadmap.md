---
title: "Roadmap"
weight: 5
---

hocon is built in phases, smallest-useful-thing first. The goal of the early phases is a
parser good enough to write and read i18n translation files; later phases close the gap to
the full HOCON specification, with the reference implementation's conformance corpus as the
eventual oracle.

| Phase | Scope | Status |
|------:|-------|:------:|
| **1** | Lexer + parser → untyped `Config` (comments, quoted/unquoted strings, nested objects, path-expression keys, arrays). | ✅ Done |
| **2** | Object merging + `withFallback` (base locale + overrides). | ✅ Done |
| **3** | Substitutions: `${path}`, `${?path}`, environment fallback, cycle detection. | ✅ Done |
| **4** | Value concatenation + durations (`10s`) and sizes (`512K`, `10MB`). | ✅ Done |
| **5** | `include` directives behind a pluggable, per-platform IO source. | ✅ Done |
| **6** | Typed decoder with case-class derivation (`config.as[A]`). | ✅ Done |
| **7** | Conformance against the reference test corpus. | Planned |

## Design notes

- **Cross-platform from the first commit.** The lexer, parser, and `Config` API are pure
  Scala in a shared source set; every test runs identically on the JVM, Scala.js, and Scala
  Native. IO and environment access — needed for `include` and substitution fallback — live
  behind small capability seams (`ConfigSource`, `EnvSource`) with per-platform
  implementations, so the core never touches a filesystem.
- **Untyped core first, typed decoder on top.** The `Config` tree and its getters are the
  foundation; the case-class decoder (`config.as[A]`) layers on top of the complete value
  model, mirroring how the typed and untyped layers separate elsewhere. Derivation is pure
  compile-time `Mirror` work, so it runs identically on all three platforms.
- **Spec-correct, not convenient.** Where the HOCON spec and a nicer-for-i18n shortcut
  disagree — most visibly the forbidden characters in unquoted strings — hocon follows the
  spec and asks you to quote, so the same files will validate against the conformance corpus
  in Phase 7.
