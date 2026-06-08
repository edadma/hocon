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

  "substitutions" - {
    "a whole-value substitution resolves against the root" in {
      val c = Hocon.parse("""
        host = localhost
        url  = ${host}
      """)
      c.getString("url") shouldBe "localhost"
    }

    "substitutions are order-independent (forward references work)" in {
      val c = Hocon.parse("""
        url  = ${host}
        host = localhost
      """)
      c.getString("url") shouldBe "localhost"
    }

    "a substitution can point into a nested path" in {
      val c = Hocon.parse("""
        server { host = example.com }
        primary = ${server.host}
      """)
      c.getString("primary") shouldBe "example.com"
    }

    "substitution chains resolve transitively" in {
      val c = Hocon.parse("""
        a = ${b}
        b = ${c}
        c = deep
      """)
      c.getString("a") shouldBe "deep"
    }

    "a substitution can copy an object subtree" in {
      val c = Hocon.parse("""
        defaults { timeout = 30, retries = 3 }
        service  = ${defaults}
      """)
      c.getInt("service.timeout") shouldBe 30
      c.getInt("service.retries") shouldBe 3
    }

    "a required substitution that is missing throws" in {
      an[UnresolvedSubstitutionException] should be thrownBy Hocon.parse("x = ${nope}")
    }

    "an optional substitution that is missing drops the field" in {
      val c = Hocon.parse("""
        a = 1
        b = ${?nope}
      """)
      c.getInt("a") shouldBe 1
      c.hasPath("b") shouldBe false
    }

    "a circular reference throws" in {
      a[CircularReferenceException] should be thrownBy Hocon.parse("""
        a = ${b}
        b = ${a}
      """)
    }

    "a self reference with no prior value is circular" in {
      a[CircularReferenceException] should be thrownBy Hocon.parse("a = ${a}")
    }

    "a missing substitution falls back to the environment" in {
      val env = EnvSource.fromMap(Map("HOME" -> "/home/ada"))
      val c   = Hocon.parse("home = ${HOME}", env)
      c.getString("home") shouldBe "/home/ada"
    }

    "config values win over the environment" in {
      val env = EnvSource.fromMap(Map("host" -> "from-env"))
      val c   = Hocon.parse("host = from-config\nx = ${host}", env)
      c.getString("x") shouldBe "from-config"
    }

    "a substitution concatenates with surrounding text" in {
      val c = Hocon.parse("""
        name = world
        greeting = hello ${name}
      """)
      c.getString("greeting") shouldBe "hello world"
    }
  }

  "value concatenation" - {
    "unquoted and quoted pieces join with interior whitespace preserved" in {
      val c = Hocon.parse("""msg = please   say "hello"  now""")
      c.getString("msg") shouldBe "please   say hello  now"
    }

    "a substitution joins on both sides" in {
      val c = Hocon.parse("""
        host = example.com
        port = 8080
        url  = "http://"${host}":"${port}
      """)
      c.getString("url") shouldBe "http://example.com:8080"
    }

    "an absent optional substitution contributes nothing" in {
      val c = Hocon.parse("""greeting = hi ${?missing}there""")
      c.getString("greeting") shouldBe "hi there"
    }

    "arrays concatenate element-wise" in {
      val c = Hocon.parse("""xs = [1, 2] [3, 4]""")
      c.getList("xs") shouldBe List(
        ConfigNumber("1"),
        ConfigNumber("2"),
        ConfigNumber("3"),
        ConfigNumber("4"),
      )
    }

    "arrays concatenate across a substitution" in {
      val c = Hocon.parse("""
        base = [a, b]
        all  = ${base} [c]
      """)
      c.getStringList("all") shouldBe List("a", "b", "c")
    }

    "objects in a concatenation deep-merge left to right" in {
      val c = Hocon.parse("""
        defaults { timeout = 30, retries = 3 }
        service  = ${defaults} { retries = 5, name = svc }
      """)
      c.getInt("service.timeout") shouldBe 30
      c.getInt("service.retries") shouldBe 5
      c.getString("service.name") shouldBe "svc"
    }

    "mixing an object with a string is rejected" in {
      a[HoconConcatException] should be thrownBy Hocon.parse("""x = pre { a = 1 }""")
    }
  }

  "durations and sizes" - {
    "duration units parse to a FiniteDuration" in {
      import scala.concurrent.duration.*
      val c = Hocon.parse("""
        a = 10s
        b = 5 minutes
        c = 500ms
        d = 250
      """)
      c.getDuration("a") shouldBe 10.seconds
      c.getDuration("b") shouldBe 5.minutes
      c.getDuration("c") shouldBe 500.millis
      c.getDuration("d") shouldBe 250.millis // bare number is milliseconds
    }

    "size units distinguish powers of 1024 and 1000" in {
      val c = Hocon.parse("""
        a = 512K
        b = 1 KiB
        c = 10MB
        d = 2 GiB
        e = 2048
      """)
      c.getBytes("a") shouldBe 512L * 1024
      c.getBytes("b") shouldBe 1024L
      c.getBytes("c") shouldBe 10L * 1000 * 1000
      c.getBytes("d") shouldBe 2L * 1024 * 1024 * 1024
      c.getBytes("e") shouldBe 2048L
    }

    "an unparseable duration or size is a wrong-type error" in {
      val c = Hocon.parse("""a = "not a duration"""")
      a[WrongTypeException] should be thrownBy c.getDuration("a")
      a[WrongTypeException] should be thrownBy c.getBytes("a")
    }

    "opt variants return None on a missing path" in {
      val c = Hocon.parse("a = 10s")
      c.getDurationOpt("a").isDefined shouldBe true
      c.getDurationOpt("missing") shouldBe None
      c.getBytesOpt("missing") shouldBe None
    }
  }

  "includes" - {
    "an include merges the named resource's fields" in {
      val src = ConfigSource.fromMap(Map("base.conf" -> "b = 2"))
      val c = Hocon.parse(
        """
        include "base.conf"
        a = 1
        """,
        src,
      )
      c.getInt("a") shouldBe 1
      c.getInt("b") shouldBe 2
    }

    "fields after an include override the included values" in {
      val src = ConfigSource.fromMap(Map("base.conf" -> "x = 1"))
      val c = Hocon.parse(
        """
        include "base.conf"
        x = 2
        """,
        src,
      )
      c.getInt("x") shouldBe 2
    }

    "an include overrides fields defined before it" in {
      val src = ConfigSource.fromMap(Map("over.conf" -> "x = 9"))
      val c = Hocon.parse(
        """
        x = 1
        include "over.conf"
        """,
        src,
      )
      c.getInt("x") shouldBe 9
    }

    "objects deep-merge across an include" in {
      val src = ConfigSource.fromMap(Map("base.conf" -> "srv { host = localhost }"))
      val c = Hocon.parse(
        """
        include "base.conf"
        srv { port = 8080 }
        """,
        src,
      )
      c.getString("srv.host") shouldBe "localhost"
      c.getInt("srv.port") shouldBe 8080
    }

    "an optional include that resolves to nothing is skipped" in {
      val c = Hocon.parse(
        """
        include "missing.conf"
        a = 1
        """,
        ConfigSource.empty,
      )
      c.getInt("a") shouldBe 1
      c.hasPath("missing.conf") shouldBe false
    }

    "a required include that resolves to nothing throws" in {
      a[IncludeException] should be thrownBy Hocon.parse(
        """include required("missing.conf")""",
        ConfigSource.empty,
      )
    }

    "the file/url/classpath qualifier forms parse and load" in {
      val src = ConfigSource.fromMap(Map("a.conf" -> "a = 1", "b.conf" -> "b = 2"))
      val c = Hocon.parse(
        """
        include file("a.conf")
        include classpath("b.conf")
        """,
        src,
      )
      c.getInt("a") shouldBe 1
      c.getInt("b") shouldBe 2
    }

    "required wraps another qualifier" in {
      val src = ConfigSource.fromMap(Map("a.conf" -> "a = 1"))
      val c   = Hocon.parse("""include required(file("a.conf"))""", src)
      c.getInt("a") shouldBe 1
    }

    "includes nest" in {
      val src = ConfigSource.fromMap(
        Map(
          "a.conf" -> "include \"b.conf\"\na = 1",
          "b.conf" -> "b = 2",
        ),
      )
      val c = Hocon.parse("""include "a.conf"""", src)
      c.getInt("a") shouldBe 1
      c.getInt("b") shouldBe 2
    }

    "a cycle of includes throws" in {
      val src = ConfigSource.fromMap(
        Map(
          "a.conf" -> "include \"b.conf\"",
          "b.conf" -> "include \"a.conf\"",
        ),
      )
      a[IncludeException] should be thrownBy Hocon.parse("""include "a.conf"""", src)
    }

    "a substitution resolves against included content" in {
      val src = ConfigSource.fromMap(Map("base.conf" -> "host = localhost"))
      val c = Hocon.parse(
        """
        include "base.conf"
        url = ${host}
        """,
        src,
      )
      c.getString("url") shouldBe "localhost"
    }

    "include is still usable as an ordinary key" in {
      val c = Hocon.parse("""include = "a value"""")
      c.getString("include") shouldBe "a value"
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
