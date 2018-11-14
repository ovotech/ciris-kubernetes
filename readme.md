## Ciris Kubernetes
Kubernetes secrets support for [Ciris][ciris] using the official [Kubernetes Java client][kubernetes-java-client].

### Getting Started
To get started with [sbt][sbt], simply add the following lines to your `build.sbt` file.

```scala
resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies += "com.ovoenergy" %% "ciris-kubernetes" % "0.9"
```

The library is published for Scala 2.11 and 2.12.

### Usage
Start with `import ciris.kubernetes._`, create an `ApiClient`, and register any authenticators. You can then set the namespace for your secrets with `secretInNamespace`, like in the following example. You can then load secrets by specifying the secret name. If there is more than one entry for the secret, you can also specify the key to retrieve.

```scala
import cats.effect.IO
import ciris._
import ciris.cats.effect._
import ciris.kubernetes._
import ciris.syntax._

final case class Config(
  appName: String,
  apiKey: Secret[String],
  username: String,
  timeout: Int
)

val config: IO[Config] =
  for {
    _ <- registerGcpAuth[IO]
    apiClient <- defaultApiClient[IO]
    secret = secretInNamespace("secrets", apiClient)
    config <- loadConfig(
      secret[Secret[String]]("apiKey"), // Key can be omitted if secret has only one entry
      secret[String]("username"),
      secret[Int]("defaults", "timeoutt") // Key is necessary if secret has multiple entries
    ) { (apiKey, username, timeout) =>
      Config(
        appName = "my-api",
        apiKey = apiKey,
        username = username,
        timeout = timeout
      )
    }.orRaiseThrowable
  } yield config
```

In the example above, the `apiKey` secret is missing, the `username` secret has multiple entries, and `timeout` was accidentally misspelled. The `loadConfig` method returns an `IO[Either[ConfigErrors, Config]]`, and we raise `ConfigErrors` as an exception with `orRaiseThrowable`.

We've now described how to load the configuration, so let's try to actually load it. We're only using `unsafeRunSync` for demonstration purposes here, you should normally never use it. As the example shows, error messages give detailed information on what went wrong.

```scala
config.unsafeRunSync()
// ciris.ConfigException: configuration loading failed with the following errors.
//
//   - Exception while reading kubernetes secret [namespace = secrets, name = apiKey]: io.kubernetes.client.ApiException: Not Found.
//   - There is more than one entry available for kubernetes secret [namespace = secrets, name = username], please specify which key to use; available keys are: admin, user.
//   - Kubernetes secret [namespace = secrets, name = defaults, key = timeoutt] exists but there is no entry with key [timeoutt]; available keys are: port, timeout.
//
//   at ciris.ConfigException$.apply(ConfigException.scala:34)
//   at ciris.ConfigErrors$.toException$extension(ConfigErrors.scala:128)
//   at ciris.syntax$EitherConfigErrorsFSyntax$.$anonfun$orRaiseThrowable$1(syntax.scala:71)
//   at cats.effect.internals.IORunLoop$.liftedTree3$1(IORunLoop.scala:207)
//   at cats.effect.internals.IORunLoop$.step(IORunLoop.scala:207)
//   at cats.effect.IO.unsafeRunTimed(IO.scala:307)
//   at cats.effect.IO.unsafeRunSync(IO.scala:242)
//   ... 36 elided
```

[cats-effect]: https://github.com/typelevel/cats-effect
[ciris]: https://cir.is
[kubernetes-java-client]: https://github.com/kubernetes-client/java
[sbt]: https://www.scala-sbt.org
