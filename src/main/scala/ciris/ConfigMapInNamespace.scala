package ciris.kubernetes

import cats.effect.Blocker
import ciris.ConfigValue
import io.kubernetes.client.openapi.ApiClient

sealed abstract class ConfigMapInNamespace {
  def apply(name: String): ConfigValue[String]

  def apply(name: String, key: String): ConfigValue[String]
}

private[kubernetes] final object ConfigMapInNamespace {
  final def apply(namespace: String, client: ApiClient, blocker: Blocker): ConfigMapInNamespace =
    new ConfigMapInNamespace {
      override final def apply(name: String): ConfigValue[String] =
        configMap(client, name, namespace, None, blocker)

      override final def apply(name: String, key: String): ConfigValue[String] =
        configMap(client, name, namespace, Some(key), blocker)

      override final def toString: String =
        s"ConfigMapInNamespace($namespace)"
    }
}
