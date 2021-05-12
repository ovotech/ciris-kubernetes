package ciris.kubernetes

import ciris.ConfigValue
import io.kubernetes.client.openapi.ApiClient

sealed abstract class SecretInNamespace[F[_]] {
  def apply(name: String): ConfigValue[F, String]

  def apply(name: String, key: String): ConfigValue[F, String]
}

private[kubernetes] final object SecretInNamespace {
  final def apply[F[_]](namespace: String, client: ApiClient): SecretInNamespace[F] =
    new SecretInNamespace[F] {
      override final def apply(name: String): ConfigValue[F, String] =
        secret(client, name, namespace, None)

      override final def apply(name: String, key: String): ConfigValue[F, String] =
        secret(client, name, namespace, Some(key))

      override final def toString: String =
        s"SecretInNamespace($namespace)"
    }
}
