## Ciris Kubernetes

Kubernetes support for [Ciris](https://cir.is) using the official [Kubernetes Java client](https://github.com/kubernetes-client/java).

### Getting Started

To get started with [sbt](https://www.scala-sbt.org), simply add the following lines to your `build.sbt` file.

```scala
resolvers += "Artifactory" at "https://kaluza.jfrog.io/artifactory/maven/"

libraryDependencies += "com.ovoenergy" %% "ciris-kubernetes" % "2.0.1"
```

The library is published for Scala 2.12 and 2.13.

### Usage

The library supports Kubernetes secrets and config maps.

#### Secrets

Start with `import ciris.kubernetes._` and then set the namespace for your secrets with `secretInNamespace`. You can then load secrets by specifying the secret name. If there is more than one entry for the secret, you can also specify the key to retrieve.

```scala
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import ciris.kubernetes._
import ciris.Secret

final case class Config(
  appName: String,
  apiKey: Secret[String],
  username: String,
  timeout: Int
)

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =

    val config =
      secretInNamespace[IO]("secrets").flatMap { secret =>
        (
          secret("apiKey").secret, // Key can be omitted if secret has only one entry
          secret("username"),
          secret("defaults", "timeoutt").as[Int] // Key is necessary if secret has multiple entries
        ).parMapN { (apiKey, username, timeout) =>
          Config(
            appName = "my-api",
            apiKey = apiKey,
            username = username,
            timeout = timeout
          )
        }
      }

    config.load[IO].as(ExitCode.Success)
}
```

In the example above, the `apiKey` secret is missing, the `username` secret has multiple entries, and `timeout` was accidentally misspelled.

```scala
// ciris.ConfigException: configuration loading failed with the following errors.
//
//   - Missing kubernetes secret [namespace = secrets, name = apiKey].
//   - There is more than one entry available for kubernetes secret [namespace = secrets, name = username], please specify which key to use; available keys are: admin, user.
//   - Kubernetes secret [namespace = secrets, name = defaults, key = timeoutt] exists but there is no entry with key [timeoutt]; available keys are: port, timeout.
```

#### Config Maps

Config maps are supported in a similar fashion to how secrets are supported.

```scala
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import ciris.kubernetes._
import ciris.Secret

final case class Config(
  appName: String,
  pizzaBrand: String,
  deliveryRadius: Int,
  isDeliveryCharge: Boolean
)

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    val config =
      configMapInNamespace[IO]("pizza").flatMap { configMap =>
        (
          configMap("pizzaBrand"), // Key can be omitted if config map has only one entry
          configMap("delivery", "radius").as[Int], // Key is necessary if config map has multiple entries
          configMap("delivery", "charge").as[Boolean]
        ).parMapN { (pizzaBrand, deliveryRadius, isDeliveryCharge) =>
          Config(
            appName = "my-pizza-api",
            pizzaBrand = pizzaBrand,
            deliveryRadius = deliveryRadius,
            isDeliveryCharge = isDeliveryCharge
          )
        }
      }

    config.load[IO].as(ExitCode.Success)

}
```


### Development

#### Publishing
In order to publish a new release, Artifactory credentials must be provided. We publish using the rac team account, which results in the artifact being released to the [public repo](https://kaluza.jfrog.io/artifactory/maven/com/ovoenergy/ciris-kubernetes_2.13).

Credentials can be set, for example in ~/.sbt/1.0/credentials.sbt:
```
credentials += Credentials("Artifactory Realm", "kaluza.jfrog.io", "<username>", "<key>")
```
replacing <username> with JFrog username and <key> with your API key.


And to release run:
```
sbt release
```
