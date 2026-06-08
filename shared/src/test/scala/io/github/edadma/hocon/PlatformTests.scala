package io.github.edadma.hocon

import io.github.edadma.cross_platform.{createTempFile, deleteFile, writeFile}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Real-IO tests for the platform seam — the actual filesystem [[ConfigSource.default]] and the live
  * [[EnvSource.system]]. They run on every platform through the cross-platform file/env API, so the
  * JVM, Scala.js (Node), and Scala Native all exercise their own adapter. The include *logic*
  * (merging, `required`, nesting, cycles) is covered separately and platform-independently via
  * [[ConfigSource.fromMap]]; these cover the thin real-IO layer beneath it.
  */
class PlatformTests extends AnyFreeSpec with Matchers:

  "the default source reads an include from the filesystem" in {
    val file = createTempFile("hocon-include", ".conf")
    try
      writeFile(file, """greeting = "hi from a file"""")
      val c = Hocon.parse(s"""include "$file"""", ConfigSource.default)
      c.getString("greeting") shouldBe "hi from a file"
    finally deleteFile(file)
  }

  "a required filesystem include that is absent throws" in {
    a[IncludeException] should be thrownBy
      Hocon.parse("""include required("/no/such/hocon/file.conf")""", ConfigSource.default)
  }

  "the system env source backs substitutions" in {
    // Compare the resolved value against the same seam, so the check is identical on every platform
    // (System.getenv on the JVM/Native, process.env under Node). Skips when the variable is unset.
    EnvSource.system.get("HOME").foreach { home =>
      val c = Hocon.parse("home = ${HOME}", EnvSource.system)
      c.getString("home") shouldBe home
    }
  }
