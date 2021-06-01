package ciris.kubernetes

import ciris.ConfigValue
import io.kubernetes.client.openapi.ApiClient

sealed abstract class ConfigMapInNamespace[F[_]] {
  def apply(name: String): ConfigValue[F, String]

  def apply(name: String, key: String): ConfigValue[F, String]
}

private[kubernetes] final object ConfigMapInNamespace {
  final def apply[F[_]](namespace: String, client: ApiClient): ConfigMapInNamespace[F] =
    new ConfigMapInNamespace[F] {
      override final def apply(name: String): ConfigValue[F, String] =
        configMap(client, name, namespace, None)

      override final def apply(name: String, key: String): ConfigValue[F, String] =
        configMap(client, name, namespace, Some(key))

      override final def toString: String =
        s"ConfigMapInNamespace($namespace)"
    }
}
