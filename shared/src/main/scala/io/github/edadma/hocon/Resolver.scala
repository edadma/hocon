package io.github.edadma.hocon

import scala.collection.immutable.ListMap

/** The second phase of parsing: replace every [[ConfigSubstitution]] in a freshly parsed tree with
  * the value it refers to.
  *
  * Substitutions resolve against the fully merged root, so they are order-independent and may point
  * forward or backward. A required `${path}` that is found nowhere — neither in the config nor in
  * the environment — raises [[UnresolvedSubstitutionException]]; an optional `${?path}` instead
  * disappears, dropping the field or array element that held it. A reference chain that closes on
  * itself raises [[CircularReferenceException]].
  */
object Resolver:

  def resolve(root: ConfigObject, env: EnvSource): ConfigObject =
    resolveValue(root, root, env, Nil) match
      case o: ConfigObject => o
      case other           => other.asInstanceOf[ConfigObject] // root is always an object

  private def resolveValue(
      v: ConfigValue,
      root: ConfigObject,
      env: EnvSource,
      stack: List[String],
  ): ConfigValue =
    v match
      case ConfigSubstitution(path, optional) => resolveSubstitution(path, optional, root, env, stack)
      case ConfigConcat(parts)                => resolveConcat(parts, root, env, stack)
      case ConfigObject(fields) =>
        val resolved = fields.iterator
          .flatMap { (k, vv) =>
            resolveValue(vv, root, env, stack) match
              case ResolveMissing => None
              case rv             => Some(k -> rv)
          }
          .to(ListMap)
        ConfigObject(resolved)
      case ConfigArray(elements) =>
        ConfigArray(elements.flatMap { e =>
          resolveValue(e, root, env, stack) match
            case ResolveMissing => None
            case rv             => Some(rv)
        })
      case other => other

  private def resolveSubstitution(
      path: String,
      optional: Boolean,
      root: ConfigObject,
      env: EnvSource,
      stack: List[String],
  ): ConfigValue =
    if stack.contains(path) then throw CircularReferenceException((path :: stack).reverse)
    lookup(root, path) match
      case Some(raw) => resolveValue(raw, root, env, path :: stack)
      case None =>
        env.get(path) match
          case Some(s)            => ConfigString(s)
          case None if optional   => ResolveMissing
          case None               => throw UnresolvedSubstitutionException(path)

  /** Collapse a value concatenation. Whitespace pieces and missing optional substitutions drop out
    * of the significant set; if every remaining piece is an array they concatenate element-wise, if
    * every one is an object they deep-merge left to right, and otherwise the whole run renders to a
    * string with interior whitespace preserved. Concatenating an object or array into a string mixes
    * incompatible kinds and is rejected.
    */
  private def resolveConcat(
      parts: List[ConfigValue],
      root: ConfigObject,
      env: EnvSource,
      stack: List[String],
  ): ConfigValue =
    val resolved = parts.map {
      case w: ConfigWhitespace => w
      case p                   => resolveValue(p, root, env, stack)
    }
    val significant = resolved.filter {
      case _: ConfigWhitespace => false
      case ResolveMissing      => false
      case _                   => true
    }
    if significant.nonEmpty && significant.forall(_.isInstanceOf[ConfigArray]) then
      ConfigArray(significant.collect { case ConfigArray(es) => es }.flatten)
    else if significant.nonEmpty && significant.forall(_.isInstanceOf[ConfigObject]) then
      significant.collect { case o: ConfigObject => o }.reduceLeft(ConfigObject.deepMerge)
    else
      val sb = StringBuilder()
      for p <- resolved do
        p match
          case ConfigWhitespace(ws) => sb ++= ws
          case ResolveMissing       => // an absent optional substitution contributes nothing
          case ConfigString(s)      => sb ++= s
          case ConfigNumber(r)      => sb ++= r
          case ConfigBoolean(b)     => sb ++= b.toString
          case ConfigNull           => sb ++= "null"
          case _: ConfigObject | _: ConfigArray =>
            throw HoconConcatException("cannot concatenate an object or array with a string")
          case other => sb ++= other.toString
      ConfigString(sb.toString)

  private def lookup(root: ConfigObject, path: String): Option[ConfigValue] =
    def go(cv: ConfigValue, segs: List[String]): Option[ConfigValue] = segs match
      case Nil => Some(cv)
      case k :: rest =>
        cv match
          case o: ConfigObject => o.fields.get(k).flatMap(go(_, rest))
          case _               => None
    go(root, path.split("\\.").toList)
