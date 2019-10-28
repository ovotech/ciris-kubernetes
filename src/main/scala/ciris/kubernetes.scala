package ciris

import cats.effect.Sync
import cats.implicits._
import io.kubernetes.client.{ApiClient, ApiException}
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.Config
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

package object kubernetes {
  final def configMapInNamespace[F[_]](
    namespace: String
  )(implicit F: Sync[F]): F[ConfigMapInNamespace] =
    defaultApiClient[F].map(configMapInNamespace(namespace, _))

  final def configMapInNamespace(namespace: String, client: ApiClient): ConfigMapInNamespace =
    ConfigMapInNamespace(namespace, client)

  final def defaultApiClient[F[_]](implicit F: Sync[F]): F[ApiClient] =
    F.delay(Config.defaultClient())

  final def secretInNamespace[F[_]](namespace: String)(
    implicit F: Sync[F]
  ): F[SecretInNamespace] =
    defaultApiClient[F].map(secretInNamespace(namespace, _))

  final def secretInNamespace(namespace: String, client: ApiClient): SecretInNamespace =
    SecretInNamespace(namespace, client)

  private[kubernetes] final def secret(
    client: ApiClient,
    name: String,
    namespace: String,
    key: Option[String]
  ): ConfigValue[String] = {
    val configKey = secretConfigKey(namespace, name, key)
    secretEntries(client, configKey, name, namespace)
      .flatMap(selectConfigEntry(key, configKey, _))
      .map(new String(_, StandardCharsets.UTF_8))
  }

  private[kubernetes] final def configMap(
    client: ApiClient,
    name: String,
    namespace: String,
    key: Option[String]
  ): ConfigValue[String] = {
    val configKey = configMapConfigKey(namespace, name, key)
    configMapEntries(client, configKey, name, namespace)
      .flatMap(selectConfigEntry(key, configKey, _))
  }

  private[this] final def selectConfigEntry[A](
    key: Option[String],
    configKey: ConfigKey,
    entries: Map[String, A]
  ): ConfigValue[A] = {
    def availableKeys = s"available keys are: ${entries.keys.toList.sorted.mkString(", ")}"

    key match {
      case Some(key) =>
        entries.get(key) match {
          case Some(value) =>
            ConfigValue.loaded(configKey, value)

          case None =>
            ConfigValue.failed {
              ConfigError {
                s"${configKey.description.capitalize} exists but there is no entry with key [$key]; $availableKeys"
              }
            }
        }

      case None if entries.size == 1 =>
        ConfigValue.loaded(configKey, entries.values.head)

      case None =>
        ConfigValue.failed {
          ConfigError {
            s"There is more than one entry available for ${configKey.description}, please specify which key to use; $availableKeys"
          }
        }
    }
  }

  private[this] final def secretEntries(
    client: ApiClient,
    configKey: ConfigKey,
    name: String,
    namespace: String
  ): ConfigValue[Map[String, Array[Byte]]] =
    ConfigValue.suspend {
      try {
        val entries =
          new CoreV1Api(client)
            .readNamespacedSecret(name, namespace, null, null, null)
            .getData
            .asScala
            .toMap

        ConfigValue.default(entries)
      } catch {
        case cause: ApiException if cause.getMessage == "Not Found" =>
          ConfigValue.missing(configKey)
      }
    }

  private[this] final def configMapEntries(
    client: ApiClient,
    configKey: ConfigKey,
    name: String,
    namespace: String
  ): ConfigValue[Map[String, String]] =
    ConfigValue.suspend {
      try {
        val entries =
          new CoreV1Api(client)
            .readNamespacedConfigMap(name, namespace, null, null, null)
            .getData
            .asScala
            .toMap

        ConfigValue.default(entries)
      } catch {
        case cause: ApiException if cause.getMessage == "Not Found" =>
          ConfigValue.missing(configKey)
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
