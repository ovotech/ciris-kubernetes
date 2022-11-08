package ciris

import cats.effect.Blocker
import cats.implicits._
import io.kubernetes.client.openapi.{ApiClient, ApiException}
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.Config
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

package object kubernetes {
  final def configMapInNamespace(
    namespace: String,
    blocker: Blocker
  ): ConfigValue[ConfigMapInNamespace] =
    defaultApiClient(blocker).map(configMapInNamespace(namespace, blocker, _))

  final def configMapInNamespace(
    namespace: String,
    blocker: Blocker,
    client: ApiClient
  ): ConfigMapInNamespace =
    ConfigMapInNamespace(namespace, client, blocker)

  final def defaultApiClient(blocker: Blocker): ConfigValue[ApiClient] =
    ConfigValue.blockOn(blocker) {
      ConfigValue.suspend {
        val client = Config.defaultClient()
        ConfigValue.default(client)
      }
    }

  final def secretInNamespace(
    namespace: String,
    blocker: Blocker
  ): ConfigValue[SecretInNamespace] =
    defaultApiClient(blocker).map(secretInNamespace(namespace, blocker, _))

  final def secretInNamespace(
    namespace: String,
    blocker: Blocker,
    client: ApiClient
  ): SecretInNamespace =
    SecretInNamespace(namespace, client, blocker)

  private[kubernetes] final def secret(
    client: ApiClient,
    name: String,
    namespace: String,
    key: Option[String],
    blocker: Blocker
  ): ConfigValue[String] = {
    val configKey = secretConfigKey(namespace, name, key)
    secretEntries(client, configKey, name, namespace, blocker)
      .flatMap(selectConfigEntry(key, configKey, _))
      .map(new String(_, StandardCharsets.UTF_8))
  }

  private[kubernetes] final def configMap(
    client: ApiClient,
    name: String,
    namespace: String,
    key: Option[String],
    blocker: Blocker
  ): ConfigValue[String] = {
    val configKey = configMapConfigKey(namespace, name, key)
    configMapEntries(client, configKey, name, namespace, blocker)
      .flatMap(selectConfigEntry(key, configKey, _))
  }

  private[this] final def selectConfigEntry[A](
    key: Option[String],
    configKey: ConfigKey,
    entries: Map[String, A]
  ): ConfigValue[A] = {

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

  private[this] final def secretEntries(
    client: ApiClient,
    configKey: ConfigKey,
    name: String,
    namespace: String,
    blocker: Blocker
  ): ConfigValue[Map[String, Array[Byte]]] =
    ConfigValue.blockOn(blocker) {
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

  private[this] final def configMapEntries(
    client: ApiClient,
    configKey: ConfigKey,
    name: String,
    namespace: String,
    blocker: Blocker
  ): ConfigValue[Map[String, String]] =
    ConfigValue.blockOn(blocker) {
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
