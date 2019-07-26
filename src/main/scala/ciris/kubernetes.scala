package ciris

import ciris.api._
import io.kubernetes.client.{ApiClient, ApiException}
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.authenticators.GCPAuthenticator
import io.kubernetes.client.util.{Config, KubeConfig}
import okio.ByteString

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object kubernetes {

  sealed trait ConfigEntryKey {
    def namespace: String
    def name: String
    def key: Option[String]

    def show: String = key match {
      case Some(key) => s"namespace = $namespace, name = $name, key = $key"
      case None      => s"namespace = $namespace, name = $name"
    }
  }

  final case class SecretKey(
    namespace: String,
    name: String,
    key: Option[String]
  ) extends ConfigEntryKey {
    override def toString: String = show
  }

  final case class ConfigMapKey(
    namespace: String,
    name: String,
    key: Option[String]
  ) extends ConfigEntryKey {
    override def toString: String = show
  }

  val SecretKeyType: ConfigKeyType[SecretKey] =
    ConfigKeyType("kubernetes secret")

  val ConfigMapKeyType: ConfigKeyType[ConfigMapKey] =
    ConfigKeyType("kubernetes configMap")

  private def fetchSecretEntries(
    client: ApiClient,
    name: String,
    namespace: String
  ): Try[Map[String, Array[Byte]]] = Try {
    val api = new CoreV1Api(client)
    api.readNamespacedSecret(name, namespace, null, null, null).getData.asScala.toMap
  }

  private def fetchConfigMapEntries(
    client: ApiClient,
    name: String,
    namespace: String
  ): Try[Map[String, String]] = Try {
    val api = new CoreV1Api(client)
    api.readNamespacedConfigMap(name, namespace, null, null, null).getData().asScala.toMap
  }

  private def parseFetchResult[K, T](
    fetchResult: Try[T],
    ConfigType: ConfigKeyType[K],
    config: K
  ): Either[ConfigError, T] =
    fetchResult match {
      case Success(entries) => Right(entries)
      case Failure(cause: ApiException) if cause.getMessage == "Not Found" =>
        Left(ConfigError.missingKey(config, ConfigType))
      case Failure(cause) =>
        Left(ConfigError.readException(config, ConfigType, cause))
    }

  private def pickEntry[V, T <: ConfigEntryKey](
    entries: Map[String, V],
    configEntry: T,
    ConfigType: ConfigKeyType[T]
  ) = {
    def availableKeys = s"available keys are: ${entries.keys.toList.sorted.mkString(", ")}"

    configEntry.key match {
      case Some(key) =>
        entries
          .get(key)
          .toRight(ConfigError {
            s"${ConfigType.name.capitalize} [$configEntry] exists but there is no entry with key [$key]; $availableKeys"
          })
      case None if entries.size == 1 =>
        Right(entries.values.head)
      case _ =>
        Left(ConfigError {
          s"There is more than one entry available for ${ConfigType.name} [$configEntry], please specify which key to use; $availableKeys"
        })
    }
  }

  private def secretSource(client: ApiClient): ConfigSource[Id, SecretKey, String] =
    ConfigSource(SecretKeyType) { secret =>
      val secretEntries =
        fetchSecretEntries(client, secret.name, secret.namespace) match {
          case fetchResult => parseFetchResult(fetchResult, SecretKeyType, secret)
        }

      val secretValueBytes = secretEntries.right
        .flatMap(pickEntry(_, secret, SecretKeyType))

      val secretValue =
        secretValueBytes.right
          .map(bytes => ByteString.of(bytes, 0, bytes.length).utf8)

      secretValue
    }

  private def configMapSource(client: ApiClient): ConfigSource[Id, ConfigMapKey, String] =
    ConfigSource(ConfigMapKeyType) { configMap =>
      val entries =
        fetchConfigMapEntries(client, configMap.name, configMap.namespace) match {
          case fetchResult => parseFetchResult(fetchResult, ConfigMapKeyType, configMap)
        }

      entries.right
        .flatMap(pickEntry(_, configMap, ConfigMapKeyType))
    }

  def defaultApiClient[F[_]](implicit F: Sync[F]): F[ApiClient] =
    F.suspend(F.pure(Config.defaultClient()))

  def registerGcpAuth[F[_]](implicit F: Sync[F]): F[Unit] =
    F.suspend(F.pure(KubeConfig.registerAuthenticator(new GCPAuthenticator)))

  def secretInNamespace[F[_]](
    namespace: String,
    apiClient: ApiClient
  )(implicit F: Sync[F]): SecretInNamespace[F] =
    new SecretInNamespace[F](
      namespace = namespace,
      apiClient = apiClient
    )

  final class SecretInNamespace[F[_]] private[kubernetes] (
    namespace: String,
    apiClient: ApiClient
  )(implicit F: Sync[F]) {
    private val source: ConfigSource[F, SecretKey, String] =
      ConfigSource.applyF(SecretKeyType) { key =>
        secretSource(apiClient)
          .suspendF[F]
          .read(key)
          .value
      }

    def apply[Value](name: String)(
      implicit decoder: ConfigDecoder[String, Value]
    ): ConfigEntry[F, SecretKey, String, Value] = {
      val secretKey =
        SecretKey(
          namespace = namespace,
          name = name,
          key = None
        )

      source.read(secretKey).decodeValue[Value]
    }

    def apply[Value](name: String, key: String)(
      implicit decoder: ConfigDecoder[String, Value]
    ): ConfigEntry[F, SecretKey, String, Value] = {
      val secretKey =
        SecretKey(
          namespace = namespace,
          name = name,
          key = Some(key)
        )

      source.read(secretKey).decodeValue[Value]
    }

    override def toString: String =
      s"SecretInNamespace($namespace)"
  }

  def configMapInNamespace[F[_]](
    namespace: String,
    apiClient: ApiClient
  )(implicit F: Sync[F]): ConfigMapInNamespace[F] =
    new ConfigMapInNamespace[F](
      namespace = namespace,
      apiClient = apiClient
    )

  final class ConfigMapInNamespace[F[_]] private[kubernetes] (
    namespace: String,
    apiClient: ApiClient
  )(implicit F: Sync[F]) {
    private val source: ConfigSource[F, ConfigMapKey, String] =
      ConfigSource.applyF(ConfigMapKeyType) { key =>
        configMapSource(apiClient)
          .suspendF[F]
          .read(key)
          .value
      }

    def apply[Value](name: String)(
      implicit decoder: ConfigDecoder[String, Value]
    ): ConfigEntry[F, ConfigMapKey, String, Value] = {
      val configMapKey =
        ConfigMapKey(
          namespace = namespace,
          name = name,
          key = None
        )

      source.read(configMapKey).decodeValue[Value]
    }

    def apply[Value](name: String, key: String)(
      implicit decoder: ConfigDecoder[String, Value]
    ): ConfigEntry[F, ConfigMapKey, String, Value] = {
      val configMapKey =
        ConfigMapKey(
          namespace = namespace,
          name = name,
          key = Some(key)
        )

      source.read(configMapKey).decodeValue[Value]
    }

    override def toString: String =
      s"ConfigMapInNamespace($namespace)"
  }

}
