package ciris.kubernetes

import cats.effect.Blocker
import ciris.ConfigValue
import io.kubernetes.client.openapi.ApiClient

sealed abstract class SecretInNamespace {
  def apply(name: String): ConfigValue[String]

  def apply(name: String, key: String): ConfigValue[String]
}

private[kubernetes] final object SecretInNamespace {
  final def apply(namespace: String, client: ApiClient, blocker: Blocker): SecretInNamespace =
    new SecretInNamespace {
      override final def apply(name: String): ConfigValue[String] =
        secret(client, name, namespace, None, blocker)

      override final def apply(name: String, key: String): ConfigValue[String] =
        secret(client, name, namespace, Some(key), blocker)

      override final def toString: String =
        s"SecretInNamespace($namespace)"
    }
}
