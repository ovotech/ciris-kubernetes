package ciris

import java.lang.reflect.Type
import java.util.{Base64, Date}

import com.google.gson._
import io.kubernetes.client.apis.CoreV1Api
import io.kubernetes.client.util.authenticators.GCPAuthenticator
import io.kubernetes.client.util.{Config, KubeConfig}
import io.kubernetes.client.{ApiClient, JSON}
import okio.ByteString
import org.joda.time.{DateTime, LocalDate}

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

  // Workaround for https://github.com/kubernetes-client/java/issues/131
  private val jsonWithBase64Decoding: JSON = {
    val json = new JSON()
    json.setGson {
      new GsonBuilder()
        .registerTypeAdapter(classOf[Date], new JSON.DateTypeAdapter)
        .registerTypeAdapter(classOf[java.sql.Date], new JSON.SqlDateTypeAdapter)
        .registerTypeAdapter(classOf[DateTime], new JSON.DateTimeTypeAdapter)
        .registerTypeAdapter(classOf[LocalDate], new json.LocalDateTypeAdapter)
        .registerTypeAdapter(classOf[Array[Byte]], new ByteArrayBase64StringTypeAdapter)
        .create()
    }
  }

  private final class SecretKey(
    val namespace: String,
    val name: String,
    val key: Option[String]
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
    val api = new CoreV1Api(client.setJSON(jsonWithBase64Decoding))
    api.readNamespacedSecret(name, namespace, null, null, null).getData.asScala.toMap
  }

  private def secretSource(client: ApiClient): ConfigSource[SecretKey] =
    ConfigSource(secretKeyType) { secret =>
      val secretEntries =
        fetchSecretEntries(client, secret.name, secret.namespace) match {
          case Success(entries) => Right(entries)
          case Failure(cause)   => Left(ConfigError.readException(secret, secretKeyType, cause))
        }

      val secretValueBytes =
        secretEntries.right.flatMap { entries =>
          def availableKeys = s"available keys are: ${entries.keys.mkString(", ")}"

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

  final class SecretInNamespace private[kubernetes] (namespace: String, client: ApiClient) {
    def apply[Value](name: String)(
      implicit reader: ConfigReader[Value]
    ): ConfigValue[Value] = {
      val secretKey =
        new SecretKey(
          namespace = namespace,
          name = name,
          key = None
        )

      ConfigValue(secretKey)(secretSource(client), reader)
    }

    def apply[Value](name: String, key: String)(
      implicit reader: ConfigReader[Value]
    ): ConfigValue[Value] = {
      val secretKey =
        new SecretKey(
          namespace = namespace,
          name = name,
          key = Some(key)
        )

      ConfigValue(secretKey)(secretSource(client), reader)
    }

    override def toString: String =
      s"SecretInNamespace($namespace)"
  }
}
