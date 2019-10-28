package ciris.kubernetes

import ciris.ConfigValue
import io.kubernetes.client.ApiClient

sealed abstract class ConfigMapInNamespace {
  def apply(name: String): ConfigValue[String]

  def apply(name: String, key: String): ConfigValue[String]
}

private[kubernetes] final object ConfigMapInNamespace {
  final def apply(namespace: String, client: ApiClient): ConfigMapInNamespace =
    new ConfigMapInNamespace {
      override final def apply(name: String): ConfigValue[String] =
        configMap(client, name, namespace, key = None)

      override final def apply(name: String, key: String): ConfigValue[String] =
        configMap(client, name, namespace, Some(key))

      override final def toString: String =
        s"ConfigMapInNamespace($namespace)"
    }
}
