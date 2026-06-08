package io.github.edadma.hocon

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class Tests extends AnyFreeSpec with Matchers:

  "scalars" - {
    "quoted strings" in {
      val c = Hocon.parse("""greeting = "Hello, world"""")
      c.getString("greeting") shouldBe "Hello, world"
    }

    "unquoted bare words and multi-word values" in {
      val c = Hocon.parse("""
        name = roamer
        title = file explorer
      """)
      c.getString("name") shouldBe "roamer"
      c.getString("title") shouldBe "file explorer"
    }

    "numbers, booleans, null" in {
      val c = Hocon.parse("""
        port = 8080
        ratio = 1.5
        big = 1e3
        enabled = true
        disabled = false
        nothing = null
      """)
      c.getInt("port") shouldBe 8080
      c.getDouble("ratio") shouldBe 1.5
      c.getDouble("big") shouldBe 1000.0
      c.getBoolean("enabled") shouldBe true
      c.getBoolean("disabled") shouldBe false
      c.hasPath("nothing") shouldBe false
    }

    "string escapes" in {
      val c = Hocon.parse(""" s = "line1\nline2\t\"q\"\\A" """)
      c.getString("s") shouldBe "line1\nline2\t\"q\"\\A"
    }

    "triple-quoted multi-line strings keep raw content" in {
      val c = Hocon.parse("s = \"\"\"a\nb\\nc\"\"\"")
      c.getString("s") shouldBe "a\nb\\nc"
    }
  }

  "objects" - {
    "nested braces" in {
      val c = Hocon.parse("""
        server {
          host = localhost
          port = 9000
        }
      """)
      c.getString("server.host") shouldBe "localhost"
      c.getInt("server.port") shouldBe 9000
      c.getConfig("server").getString("host") shouldBe "localhost"
    }

    "object value without a separator" in {
      val c = Hocon.parse("a { b = 1 }")
      c.getInt("a.b") shouldBe 1
    }

    "path-expression keys expand into nested objects" in {
      val c = Hocon.parse("""
        cart.items = "{count} items"
        a.b.c = deep
      """)
      c.getString("cart.items") shouldBe "{count} items"
      c.getString("a.b.c") shouldBe "deep"
    }

    "sibling path keys merge into one object" in {
      val c = Hocon.parse("""
        a.b = 1
        a.c = 2
      """)
      c.getInt("a.b") shouldBe 1
      c.getInt("a.c") shouldBe 2
    }

    "duplicate object keys deep-merge" in {
      val c = Hocon.parse("""
        en { nav { home = Home } }
        en { greeting = "Hi" }
        en { nav { about = About } }
      """)
      c.getString("en.nav.home") shouldBe "Home"
      c.getString("en.nav.about") shouldBe "About"
      c.getString("en.greeting") shouldBe "Hi"
    }

    "optional root braces" in {
      val c = Hocon.parse("""{ x = 1, y = 2 }""")
      c.getInt("x") shouldBe 1
      c.getInt("y") shouldBe 2
    }
  }

  "arrays" - {
    "string and number lists" in {
      val c = Hocon.parse("""
        names = ["alice", "bob", carol]
        ports = [1, 2, 3]
      """)
      c.getStringList("names") shouldBe List("alice", "bob", "carol")
      c.getList("ports") shouldBe List(ConfigNumber("1"), ConfigNumber("2"), ConfigNumber("3"))
    }

    "newline-separated elements and trailing commas" in {
      val c = Hocon.parse("""
        xs = [
          1
          2
          3,
        ]
      """)
      c.getList("xs") should have size 3
    }

    "arrays of objects" in {
      val c = Hocon.parse("""
        users = [
          { name = a },
          { name = b }
        ]
      """)
      val users = c.getList("users")
      users should have size 2
      users.head shouldBe a[ConfigObject]
    }
  }

  "comments and separators" - {
    "hash and slash comments are ignored" in {
      val c = Hocon.parse("""
        # a hash comment
        a = 1   // trailing slash comment
        // full-line slash comment
        b = 2
      """)
      c.getInt("a") shouldBe 1
      c.getInt("b") shouldBe 2
    }

    "commas and newlines both separate fields" in {
      val c = Hocon.parse("a = 1, b = 2\nc = 3")
      c.getInt("a") shouldBe 1
      c.getInt("b") shouldBe 2
      c.getInt("c") shouldBe 3
    }
  }

  "Config API" - {
    "hasPath distinguishes present, missing, and null" in {
      val c = Hocon.parse("a = 1\nz = null")
      c.hasPath("a") shouldBe true
      c.hasPath("missing") shouldBe false
      c.hasPath("z") shouldBe false
    }

    "opt getters return None on missing path" in {
      val c = Hocon.parse("a = 1")
      c.getIntOpt("a") shouldBe Some(1)
      c.getIntOpt("missing") shouldBe None
      c.getStringOpt("missing") shouldBe None
    }

    "missing path throws MissingPathException" in {
      val c = Hocon.parse("a = 1")
      a[MissingPathException] should be thrownBy c.getString("nope")
    }

    "wrong type throws WrongTypeException" in {
      val c = Hocon.parse("""a = "not a number"""")
      a[WrongTypeException] should be thrownBy c.getInt("a")
    }
  }

  "errors" - {
    "a forbidden unquoted character is a parse error" in {
      a[ParseError] should be thrownBy Hocon.parse("greeting = Are you sure?")
    }

    "unterminated string is a parse error" in {
      a[ParseError] should be thrownBy Hocon.parse("s = \"oops")
    }

    "unclosed object is a parse error" in {
      a[ParseError] should be thrownBy Hocon.parse("a { b = 1")
    }
  }

  "merging and fallback" - {
    "withFallback fills missing keys and lets this config win" in {
      val base     = Hocon.parse("a = 1\nb = base")
      val override_ = Hocon.parse("b = over\nc = 3")
      val merged   = override_.withFallback(base)
      merged.getInt("a") shouldBe 1       // only in base
      merged.getString("b") shouldBe "over" // this config wins
      merged.getInt("c") shouldBe 3       // only in this config
    }

    "objects merge recursively, scalars and arrays replace" in {
      val base = Hocon.parse("""
        server { host = localhost, port = 80, tags = [a, b] }
      """)
      val over = Hocon.parse("""
        server { port = 9000, tags = [c] }
      """)
      val merged = over.withFallback(base)
      merged.getString("server.host") shouldBe "localhost" // kept from base
      merged.getInt("server.port") shouldBe 9000           // overridden
      merged.getStringList("server.tags") shouldBe List("c") // arrays replace, not concat
    }

    "null in the override shadows (unsets) the fallback value" in {
      val base   = Hocon.parse("a = present")
      val over   = Hocon.parse("a = null")
      val merged = over.withFallback(base)
      merged.hasPath("a") shouldBe false
      a[MissingPathException] should be thrownBy merged.getString("a")
    }

    "Hocon.load merges in order with later winning" in {
      val merged = Hocon.load(
        Hocon.parse("a = 1\nb = 1\nc = 1"),
        Hocon.parse("b = 2\nc = 2"),
        Hocon.parse("c = 3"),
      )
      merged.getInt("a") shouldBe 1
      merged.getInt("b") shouldBe 2
      merged.getInt("c") shouldBe 3
    }

    "Hocon.load with no arguments is the empty config" in {
      Hocon.load().hasPath("anything") shouldBe false
    }
  }

  "i18n usage" - {
    "a realistic translation file parses" in {
      val c = Hocon.parse("""
        en {
          greeting = "Hello, world"
          nav { home = "Home", about = "About" }
          cart.items = "{count} items"
        }
      """)
      val en = c.getConfig("en")
      en.getString("greeting") shouldBe "Hello, world"
      en.getString("nav.home") shouldBe "Home"
      en.getString("nav.about") shouldBe "About"
      en.getString("cart.items") shouldBe "{count} items"
    }

    "Messages fills placeholders" in {
      val c = Hocon.parse("""
        cart.items = "{count} items in {who}'s cart"
        plain = "no placeholders"
      """)
      val m = Messages(c)
      m("cart.items", "count" -> 3, "who" -> "Ed") shouldBe "3 items in Ed's cart"
      m("plain") shouldBe "no placeholders"
    }

    "Messages leaves unknown placeholders intact" in {
      val c = Hocon.parse("""msg = "hi {name}"""")
      Messages(c)("msg", "other" -> 1) shouldBe "hi {name}"
    }

    "a partial locale falls back to the base locale" in {
      val base = Hocon.parse("""
        greeting = "Hello"
        farewell = "Goodbye"
        nav { home = "Home", about = "About" }
      """)
      val frFR = Hocon.parse("""
        greeting = "Bonjour"
        nav { home = "Accueil" }
      """)
      val m = Messages(frFR.withFallback(base))
      m("greeting") shouldBe "Bonjour"   // translated
      m("farewell") shouldBe "Goodbye"   // falls back to base
      m("nav.home") shouldBe "Accueil"   // translated
      m("nav.about") shouldBe "About"    // falls back to base
    }
  }
