package io.github.edadma.hocon

import java.nio.file.Files
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** JVM-only tests for the real platform IO seam — the actual filesystem [[ConfigSource.default]] and
  * the live [[EnvSource.system]]. The include *logic* (merging, required, nesting, cycles) is proven
  * platform-independently in the shared suite via `ConfigSource.fromMap`; these cover the thin JVM
  * adapter that backs it with `java.nio` and `System.getenv`.
  */
class PlatformTests extends AnyFreeSpec with Matchers:

  "the default source reads an include from the filesystem" in {
    val file = Files.createTempFile("hocon-include", ".conf")
    try
      Files.writeString(file, """greeting = "hi from a file"""")
      val c = Hocon.parse(s"""include "${file.toAbsolutePath}"""", ConfigSource.default)
      c.getString("greeting") shouldBe "hi from a file"
    finally Files.deleteIfExists(file)
  }

  "a required filesystem include that is absent throws" in {
    a[IncludeException] should be thrownBy
      Hocon.parse("""include required("/no/such/hocon/file.conf")""", ConfigSource.default)
  }

  "the system env source backs substitutions" in {
    Option(System.getenv("HOME")).foreach { home =>
      val c = Hocon.parse("home = ${HOME}", EnvSource.system)
      c.getString("home") shouldBe home
    }
  }
