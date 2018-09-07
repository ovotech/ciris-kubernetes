package ciris

import _root_.cats.effect.{Concurrent, IO}
import ciris.api._
import ciris.api.syntax._
import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.authenticators.GCPAuthenticator
import io.kubernetes.client.util.{Config, KubeConfig}
import okio.ByteString

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object kubernetes {
  final case class SecretKey(
    namespace: String,
    name: String,
    key: Option[String]
  ) {
    override def toString: String = key match {
      case Some(key) => s"namespace = $namespace, name = $name, key = $key"
      case None      => s"namespace = $namespace, name = $name"
    }
  }

  private val secretKeyType: ConfigKeyType[SecretKey] =
    ConfigKeyType("kubernetes secret")

  private def fetchSecretEntries(
    client: ApiClient,
    name: String,
    namespace: String
  ): Try[Map[String, Array[Byte]]] = Try {
    val api = new CoreV1Api(client)
    api.readNamespacedSecret(name, namespace, null, null, null).getData.asScala.toMap
  }

  private def secretSource(client: ApiClient): ConfigSource[Id, SecretKey, String] =
    ConfigSource(secretKeyType) { secret =>
      val secretEntries =
        fetchSecretEntries(client, secret.name, secret.namespace) match {
          case Success(entries) => Right(entries)
          case Failure(cause)   => Left(ConfigError.readException(secret, secretKeyType, cause))
        }

      val secretValueBytes =
        secretEntries.right.flatMap { entries =>
          def availableKeys = s"available keys are: ${entries.keys.toList.sorted.mkString(", ")}"

          secret.key match {
            case Some(key) =>
              entries
                .get(key)
                .toRight(ConfigError {
                  s"${secretKeyType.name.capitalize} [$secret] exists but there is no entry with key [$key]; $availableKeys"
                })
            case None if entries.size == 1 =>
              Right(entries.values.head)
            case _ =>
              Left(ConfigError {
                s"There is more than one entry available for ${secretKeyType.name} [$secret], please specify which key to use; $availableKeys"
              })
          }
        }

      val secretValue =
        secretValueBytes.right
          .map(bytes => ByteString.of(bytes, 0, bytes.length).utf8)

      secretValue
    }

  def defaultApiClient[F[_]](implicit F: Concurrent[F]): F[F[ApiClient]] =
    Concurrent.memoize(F.delay(Config.defaultClient()))

  def registerGcpAuthenticator[F[_]](implicit F: Concurrent[F]): F[F[Unit]] =
    Concurrent.memoize(F.delay(KubeConfig.registerAuthenticator(new GCPAuthenticator)))

  def secretInNamespace[F[_]: Sync](
    namespace: String,
    apiClient: F[ApiClient],
    registerAuthenticator: F[Unit]
  ): SecretInNamespace[F] =
    new SecretInNamespace[F](
      namespace = namespace,
      apiClient = apiClient,
      registerAuthenticator = registerAuthenticator
    )

  final class SecretInNamespace[F[_]: Sync] private[kubernetes] (
    namespace: String,
    apiClient: F[ApiClient],
    registerAuthenticator: F[Unit]
  ) {
    private val source: ConfigSource[F, SecretKey, String] =
      ConfigSource.applyF(secretKeyType) { key =>
        for {
          _ <- registerAuthenticator
          client <- apiClient
          source = secretSource(client).suspendF[F]
          value <- source.read(key).value
        } yield value
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
}
