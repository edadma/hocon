package io.github.edadma.hocon

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Conformance tests derived from the normative rules and examples in the HOCON specification
  * (lightbend/config `HOCON.md`). Each group cites the spec feature it exercises. Cases that depend
  * on the JVM runtime (system properties, classpath/URL include semantics) are out of scope for this
  * pure-format parser and are not represented here.
  */
class ConformanceTests extends AnyFreeSpec with Matchers:

  "path expressions as keys" - {
    "a dotted unquoted key nests objects" in {
      val c = Hocon.parse("foo.bar : 42")
      c.getInt("foo.bar") shouldBe 42
      c.getConfig("foo").getInt("bar") shouldBe 42
    }

    "a three-element path with one quoted segment keeps the dot literal" in {
      // foo."bar.baz" : 42  →  path elements: foo, "bar.baz"
      val c = Hocon.parse("""foo."bar.baz" : 42""")
      // The literal-dot key is addressable through the parsed structure, not the dotted-string API.
      c.getConfig("foo").root.fields.keySet shouldBe Set("bar.baz")
      c.getConfig("foo").root.fields("bar.baz") shouldBe ConfigNumber("42")
    }

    "an unquoted key with interior whitespace is a single element" in {
      // a b c : 42  is equivalent to  "a b c" : 42
      val c = Hocon.parse("a b c : 42")
      c.root.fields.keySet shouldBe Set("a b c")
      c.getInt("a b c") shouldBe 42
    }

    "a numeric key is treated as a string element" in {
      val c = Hocon.parse("3 : 42")
      c.root.fields.keySet shouldBe Set("3")
      c.getInt("3") shouldBe 42
    }

    "a decimal key splits on the period into nested objects" in {
      // 3.14 : 42  →  "3" : { "14" : 42 }
      val c = Hocon.parse("3.14 : 42")
      c.getConfig("3").getInt("14") shouldBe 42
    }

    "a fully quoted key may contain anything, including a dot" in {
      val c = Hocon.parse(""""a.b" : 42""")
      c.root.fields.keySet shouldBe Set("a.b")
    }

    "a leading-and-trailing quoted key preserves interior spaces" in {
      val c = Hocon.parse("""" spaced " : 42""")
      c.root.fields.keySet shouldBe Set(" spaced ")
    }

    "mixed quoted and unquoted segments combine into one element" in {
      // foo"bar" with no separating dot is the single element foobar
      val c = Hocon.parse("""pre"fix".x : 1""")
      c.getConfig("prefix").getInt("x") shouldBe 1
    }

    "an empty path element is a parse error" in {
      an[ParseError] should be thrownBy Hocon.parse("a..b : 1")
    }
  }

  "field-append with +=" - {
    "appends to an existing array" in {
      val c = Hocon.parse("""
        a = [1, 2]
        a += 3
      """)
      c.getList("a").map { case ConfigNumber(r) => r.toInt; case _ => fail() } shouldBe List(1, 2, 3)
    }

    "starts a fresh array when the key is absent" in {
      val c = Hocon.parse("a += 1")
      c.getList("a").map { case ConfigNumber(r) => r.toInt; case _ => fail() } shouldBe List(1)
    }

    "chains across several appends" in {
      val c = Hocon.parse("""
        a += 1
        a += 2
        a += 3
      """)
      c.getList("a").map { case ConfigNumber(r) => r.toInt; case _ => fail() } shouldBe List(1, 2, 3)
    }

    "appends a whole value as a single element" in {
      val c = Hocon.parse("""
        a = ["x"]
        a += [1, 2]
      """)
      val xs = c.getList("a")
      xs.head shouldBe ConfigString("x")
      xs(1) shouldBe ConfigArray(List(ConfigNumber("1"), ConfigNumber("2")))
    }

    "appends within a nested object" in {
      val c = Hocon.parse("""
        server { ports = [80] }
        server { ports += 443 }
      """)
      c.getConfig("server").getList("ports").map { case ConfigNumber(r) => r.toInt; case _ => fail() } shouldBe
        List(80, 443)
    }

    "appends a string element with units left as text" in {
      val c = Hocon.parse("""
        path = ["/bin"]
        path += "/usr/bin"
      """)
      c.getStringList("path") shouldBe List("/bin", "/usr/bin")
    }
  }

  "self-referential substitutions" - {
    "a key may refer to its own previous value" in {
      val c = Hocon.parse("""
        a = 1
        a = ${a}
      """)
      c.getInt("a") shouldBe 1
    }

    "a self-referential value concatenation extends the prior value" in {
      // path : ${path} [ /usr/bin ]   with a prior path
      val c = Hocon.parse("""
        path = [/bin]
        path = ${path} [/usr/bin]
      """)
      c.getStringList("path") shouldBe List("/bin", "/usr/bin")
    }

    "a self-reference with no prior value is an unbreakable cycle" in {
      an[CircularReferenceException] should be thrownBy Hocon.parse("a = ${a}")
    }
  }

  "substitution path expressions" - {
    "a quoted segment in a substitution keeps the dot literal" in {
      val c = Hocon.parse("""
        foo { "bar.baz" = 10 }
        x = ${foo."bar.baz"}
      """)
      c.getInt("x") shouldBe 10
    }

    "a dotted substitution path descends into nested objects" in {
      val c = Hocon.parse("""
        a { b { c = 7 } }
        x = ${a.b.c}
      """)
      c.getInt("x") shouldBe 7
    }
  }

  "value concatenation" - {
    "simple values concatenate into a string preserving interior whitespace" in {
      val c = Hocon.parse("x = foo bar baz")
      c.getString("x") shouldBe "foo bar baz"
    }

    "arrays concatenate into one array" in {
      val c = Hocon.parse("a = [1, 2] [3, 4]")
      c.getList("a").map { case ConfigNumber(r) => r.toInt; case _ => fail() } shouldBe List(1, 2, 3, 4)
    }

    "objects concatenate by merging" in {
      val c = Hocon.parse("a = { b = 1 } { c = 2 }")
      c.getConfig("a").getInt("b") shouldBe 1
      c.getConfig("a").getInt("c") shouldBe 2
    }

    "a single boolean is a boolean, not a string" in {
      val c = Hocon.parse("a = true")
      c.getBoolean("a") shouldBe true
    }

    "leading and trailing whitespace around a value is discarded" in {
      val c = Hocon.parse("x =    foo bar   ")
      c.getString("x") shouldBe "foo bar"
    }
  }

  "duplicate keys and merging" - {
    "later non-object values override earlier ones" in {
      val c = Hocon.parse("""
        a = 1
        a = 2
      """)
      c.getInt("a") shouldBe 2
    }

    "two object values for the same key merge" in {
      val c = Hocon.parse("""
        foo = { a = 42 }
        foo = { b = 43 }
      """)
      c.getInt("foo.a") shouldBe 42
      c.getInt("foo.b") shouldBe 43
    }

    "an intervening null breaks the merge" in {
      val c = Hocon.parse("""
        foo = { a = 42 }
        foo = null
        foo = { b = 43 }
      """)
      c.hasPath("foo.a") shouldBe false
      c.getInt("foo.b") shouldBe 43
    }
  }

  "numbers" - {
    "the original literal text is preserved for rendering" in {
      val c = Hocon.parse("x = 1e5")
      // kept as written, not normalized
      c.getValue("x") shouldBe ConfigNumber("1e5")
      c.getDouble("x") shouldBe 100000.0
    }

    "a negative decimal parses" in {
      val c = Hocon.parse("x = -2.5")
      c.getDouble("x") shouldBe -2.5
    }
  }

  "root value" - {
    "a document may have an array root" in {
      Hocon.parseValue("[1, 2, 3]") shouldBe
        ConfigArray(List(ConfigNumber("1"), ConfigNumber("2"), ConfigNumber("3")))
    }

    "an array root allows newline separators and a trailing comma" in {
      val v = Hocon.parseValue("""
        [
          a
          b,
        ]
      """)
      v shouldBe ConfigArray(List(ConfigString("a"), ConfigString("b")))
    }

    "an array root may hold objects" in {
      Hocon.parseValue("""[ { x = 1 }, { x = 2 } ]""") shouldBe
        ConfigArray(List(
          ConfigObject(scala.collection.immutable.ListMap("x" -> ConfigNumber("1"))),
          ConfigObject(scala.collection.immutable.ListMap("x" -> ConfigNumber("2"))),
        ))
    }

    "substitutions resolve inside an array root" in {
      // With no object root to look into, only the environment can satisfy a reference.
      val env = EnvSource.fromMap(Map("host" -> "localhost"))
      Hocon.parseValue("[ ${host}, ${?missing} ]", env) shouldBe
        ConfigArray(List(ConfigString("localhost")))
    }

    "parseValue still returns an object for an object root" in {
      Hocon.parseValue("a = 1") shouldBe
        ConfigObject(scala.collection.immutable.ListMap("a" -> ConfigNumber("1")))
    }

    "parse rejects an array root with a clear type error" in {
      an[WrongTypeException] should be thrownBy Hocon.parse("[1, 2, 3]")
    }
  }
