package io.github.edadma.hocon

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** JVM-only tests for the include mechanisms that exist only on the JVM — classpath resources and
  * URLs. Filesystem and environment IO are exercised on every platform by the shared [[PlatformTests]];
  * these cover the `classpath(...)`/`url(...)` qualifiers that Scala.js and Scala Native cannot serve.
  */
class JvmPlatformTests extends AnyFreeSpec with Matchers:

  "the default source loads a classpath include" in {
    val c = Hocon.parse(
      """include classpath("hocon-classpath-test.conf")""",
      ConfigSource.default,
    )
    c.getString("from-classpath") shouldBe "loaded via classpath"
  }

  "the bare include form finds a classpath resource heuristically" in {
    val c = Hocon.parse(
      """include "hocon-classpath-test.conf"""",
      ConfigSource.default,
    )
    c.getString("from-classpath") shouldBe "loaded via classpath"
  }

  "a required classpath include that is absent throws" in {
    a[IncludeException] should be thrownBy
      Hocon.parse("""include required(classpath("no-such-resource.conf"))""", ConfigSource.default)
  }
