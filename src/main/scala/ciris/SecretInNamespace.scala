package ciris.kubernetes

import ciris.ConfigValue
import io.kubernetes.client.ApiClient

sealed abstract class SecretInNamespace {
  def apply(name: String): ConfigValue[String]

  def apply(name: String, key: String): ConfigValue[String]
}

private[kubernetes] final object SecretInNamespace {
  final def apply(namespace: String, client: ApiClient): SecretInNamespace =
    new SecretInNamespace {
      override final def apply(name: String): ConfigValue[String] =
        secret(client, name, namespace, key = None)

      override final def apply(name: String, key: String): ConfigValue[String] =
        secret(client, name, namespace, Some(key))

      override final def toString: String =
        s"SecretInNamespace($namespace)"
    }
}
