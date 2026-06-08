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

  private def lookup(root: ConfigObject, path: String): Option[ConfigValue] =
    def go(cv: ConfigValue, segs: List[String]): Option[ConfigValue] = segs match
      case Nil => Some(cv)
      case k :: rest =>
        cv match
          case o: ConfigObject => o.fields.get(k).flatMap(go(_, rest))
          case _               => None
    go(root, path.split("\\.").toList)
