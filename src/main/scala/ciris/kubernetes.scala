package ciris

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.Config

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._ // cannot be upgraded to scala.jdk.CollectionConverters because 2.12 does not support

package object kubernetes {
  final def configMapInNamespace[F[_]](
    namespace: String
  ): ConfigValue[F, ConfigMapInNamespace[F]] =
    defaultApiClient.map(configMapInNamespace(namespace, _))

  final def configMapInNamespace[F[_]](
    namespace: String,
    client: ApiClient
  ): ConfigMapInNamespace[F] =
    ConfigMapInNamespace(namespace, client)

  final def defaultApiClient[F[_]]: ConfigValue[F, ApiClient] =
    ConfigValue.blocking {
      ConfigValue.suspend {
        val client = Config.defaultClient()
        ConfigValue.default(client)
      }
    }

  final def secretInNamespace[F[_]](
    namespace: String
  ): ConfigValue[F, SecretInNamespace[F]] =
    defaultApiClient.map(secretInNamespace(namespace, _))

  final def secretInNamespace[F[_]](
    namespace: String,
    client: ApiClient
  ): SecretInNamespace[F] =
    SecretInNamespace(namespace, client)

  private[kubernetes] final def secret[F[_]](
    client: ApiClient,
    name: String,
    namespace: String,
    key: Option[String]
  ): ConfigValue[F, String] = {
    val configKey = secretConfigKey(namespace, name, key)
    secretEntries(client, configKey, name, namespace)
      .flatMap(selectConfigEntry(key, configKey, _))
      .map(new String(_, StandardCharsets.UTF_8))
  }

  private[kubernetes] final def configMap[F[_]](
    client: ApiClient,
    name: String,
    namespace: String,
    key: Option[String]
  ): ConfigValue[F, String] = {
    val configKey = configMapConfigKey(namespace, name, key)
    configMapEntries(client, configKey, name, namespace)
      .flatMap(selectConfigEntry(key, configKey, _))
  }

  private[this] final def selectConfigEntry[F[_], A](
    key: Option[String],
    configKey: ConfigKey,
    entries: Map[String, A]
  ): ConfigValue[F, A] = {

    key match {
      case Some(key) =>
        entries.get(key) match {
          case Some(value) => ConfigValue.loaded(configKey, value)
          case None        => ConfigValue.missing(configKey)
        }

      case None if entries.size == 0 => ConfigValue.missing(configKey)

      case None if entries.size == 1 => ConfigValue.loaded(configKey, entries.values.head)

      case None =>
        ConfigValue.failed {
          ConfigError {
            s"There is more than one entry available for ${configKey.description}, please specify " +
              s"which key to use; available keys are: ${entries.keys.toList.sorted.mkString(", ")}"
          }
        }
    }
  }

  private[this] final def secretEntries[F[_]](
    client: ApiClient,
    configKey: ConfigKey,
    name: String,
    namespace: String
  ): ConfigValue[F, Map[String, Array[Byte]]] =
    ConfigValue.blocking {
      ConfigValue.suspend {
        try {
          val entries =
            new CoreV1Api(client)
              .readNamespacedSecret(name, namespace, null)
              .getData
              .asScala
              .toMap

          ConfigValue.default(entries)
        } catch {
          case cause: ApiException if cause.getMessage == "Not Found" || cause.getCode == 404 =>
            ConfigValue.missing(configKey)
        }
      }
    }

  private[this] final def configMapEntries[F[_]](
    client: ApiClient,
    configKey: ConfigKey,
    name: String,
    namespace: String
  ): ConfigValue[F, Map[String, String]] =
    ConfigValue.blocking {
      ConfigValue.suspend {
        try {
          val entries =
            new CoreV1Api(client)
              .readNamespacedConfigMap(name, namespace, null)
              .getData
              .asScala
              .toMap

          ConfigValue.default(entries)
        } catch {
          case cause: ApiException if cause.getMessage == "Not Found" || cause.getCode == 404 =>
            ConfigValue.missing(configKey)
        }
      }
    }

  private[this] final def secretConfigKey(
    namespace: String,
    name: String,
    key: Option[String]
  ): ConfigKey =
    ConfigKey(key match {
      case Some(key) => s"kubernetes secret [namespace = $namespace, name = $name, key = $key]"
      case None      => s"kubernetes secret [namespace = $namespace, name = $name]"
    })

  private[this] final def configMapConfigKey(
    namespace: String,
    name: String,
    key: Option[String]
  ): ConfigKey =
    ConfigKey(key match {
      case Some(key) => s"kubernetes config map [namespace = $namespace, name = $name, key = $key]"
      case None      => s"kubernetes config map [namespace = $namespace, name = $name]"
    })
}
