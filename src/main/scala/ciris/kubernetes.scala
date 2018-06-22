package ciris

import java.lang.reflect.Type
import java.util.Base64

import ciris.api._
import ciris.api.syntax._
import com.google.gson._
import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.authenticators.GCPAuthenticator
import io.kubernetes.client.util.{Config, KubeConfig}
import okio.ByteString

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object kubernetes {
  KubeConfig.registerAuthenticator(new GCPAuthenticator())

  private final class ByteArrayBase64StringTypeAdapter
      extends JsonSerializer[Array[Byte]]
      with JsonDeserializer[Array[Byte]] {

    override def serialize(
      src: Array[Byte],
      typeOfSrc: Type,
      context: JsonSerializationContext
    ): JsonElement = {
      new JsonPrimitive(Base64.getEncoder.encodeToString(src))
    }

    override def deserialize(
      json: JsonElement,
      typeOfT: Type,
      context: JsonDeserializationContext
    ): Array[Byte] = {
      Base64.getDecoder.decode(json.getAsString)
    }
  }

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
            case Some(key) => entries.get(key).toRight(ConfigError(s"${secretKeyType.name.capitalize} [$secret] exists but there is no entry with key [$key]; $availableKeys"))
            case None if entries.size == 1 => Right(entries.values.head)
            case _ => Left(ConfigError(s"There is more than one entry available for ${secretKeyType.name} [$secret], please specify which key to use; $availableKeys"))
          }
        }

      val secretValue =
        secretValueBytes.right
          .map(bytes => ByteString.of(bytes, 0, bytes.length).utf8)

      secretValue
    }

  def secretInNamespace(namespace: String)(
    implicit client: ApiClient = Config.defaultClient()
  ): SecretInNamespace = {
    new SecretInNamespace(namespace, client)
  }

  def secretInNamespaceF[F[_]: Sync](
    namespace: String,
    client: F[ApiClient]
  ): SecretInNamespaceF[F] = {
    new SecretInNamespaceF[F](namespace, client)
  }

  final class SecretInNamespaceF[F[_]: Sync] private[kubernetes] (
    namespace: String,
    client: F[ApiClient]
  ) {
    private val source: ConfigSource[F, SecretKey, String] =
      ConfigSource.applyF(secretKeyType) { key =>
        client.flatMap(secretSource(_).suspendF[F].read(key).value)
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
  }

  final class SecretInNamespace private[kubernetes] (namespace: String, client: ApiClient) {
    private val source: ConfigSource[Id, SecretKey, String] =
      secretSource(client)

    def apply[Value](name: String)(
      implicit decoder: ConfigDecoder[String, Value]
    ): ConfigEntry[Id, SecretKey, String, Value] = {
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
    ): ConfigEntry[Id, SecretKey, String, Value] = {
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
