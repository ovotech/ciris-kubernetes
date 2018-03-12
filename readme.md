## Ciris Kubernetes
Kubernetes secrets support for [Ciris][ciris] using the official [Kubernetes Java client][kubernetes-java-client].

### Getting Started
To get started with [SBT][sbt], simply add the following lines to your `build.sbt` file.

```scala
resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies += "com.ovoenergy" %% "ciris-kubernetes" % "0.4"
```

The library is published for Scala 2.10, 2.11, and 2.12.

### Usage
Simply `import ciris.kubernetes._` and set the namespace for your secrets with `secretInNamespace`, like in the following example. You can then load secrets by specifying the secret name. If there is more than one entry for the secret, you can also specify the key to retrieve. There is also a pure and safe version `secretInNamespaceF` available for use; see the [Pure Usage](#pure-usage) section below for how to use it.

```scala
import ciris._
import ciris.syntax._
import ciris.kubernetes._

final case class Config(
  appName: String,
  apiKey: Secret[String],
  username: String,
  timeout: Int
)

// Set the namespace in which the secrets reside
val secret = secretInNamespace("secrets")

val config =
  loadConfig(
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
  }
```

In the example above, the `apiKey` secret is missing, the `username` secret has multiple entries, and `timeout` was accidentally misspelled. The `loadConfig` method returns an `Either[ConfigErrors, Config]`, and we can quickly take a look at the errors with `config.orThrow()`, like in the following example.

```scala
config.orThrow()
// ciris.ConfigException: configuration loading failed with the following errors.
//
//   - Exception while reading kubernetes secret [namespace = secrets, name = apiKey]: io.kubernetes.client.ApiException: Not Found.
//   - There is more than one entry available for kubernetes secret [namespace = secrets, name = username], please specify which key to use; available keys are: admin, user.
//   - Kubernetes secret [namespace = secrets, name = defaults, key = timeoutt] exists but there is no entry with key [timeoutt]; available keys are: port, timeout.
//
//   at ciris.ConfigException$.apply(ConfigException.scala:33)
//   at ciris.ConfigErrors$.toException$extension(ConfigErrors.scala:109)
//   at ciris.syntax$EitherConfigErrorsSyntax$.$anonfun$orThrow$1(syntax.scala:22)
//   at ciris.syntax$EitherConfigErrorsSyntax$.$anonfun$orThrow$1$adapted(syntax.scala:22)
//   at scala.util.Either.fold(Either.scala:189)
//   at ciris.syntax$EitherConfigErrorsSyntax$.orThrow$extension(syntax.scala:23)
//   ... 43 elided
```

As the example above shows, error messages give detailed information on what went wrong.

#### Pure Usage
The `secretInNamespace` function described above is not pure, but mostly safe to use. More specifically, if no implicit `ApiClient` is specified, it will use the default client, which might throw an `IOException` during creation. If the client was created successfully, reading is safe, but not pure. Both of these issues can be circumvented with `secretInNamespaceF`.

The `secretInNamespaceF` function accepts an `F[ApiClient]` where `F[_]: Sync`, and then suspends reading values into effect `F`. For example, `F` can be `IO` from [cats-effect][cats-effect], like in the following example. The example is the same as shown above, except it's using `secretInNamespaceF` for purity and safety.

```scala
import cats.Eval
import cats.effect.IO
import ciris._
import ciris.kubernetes._
import ciris.cats.effect._

final case class Config(
  appName: String,
  apiKey: Secret[String],
  username: String,
  timeout: Int
)

// Suspend creating the ApiClient
val client = IO.eval(Eval.later(io.kubernetes.client.util.Config.defaultClient()))

// Set the namespace in which the secrets reside
val secret = secretInNamespaceF("secrets", client)

val config =
  loadConfig(
    secret[Secret[String]]("apiKey"),
    secret[String]("username"),
    secret[Int]("defaults", "timeoutt")
  ) { (apiKey, username, timeout) =>
    Config(
      appName = "my-api",
      apiKey = apiKey,
      username = username,
      timeout = timeout
    )
  }
```

Compared to when using `secretInNamespace`, we now get an `IO[Either[ConfigErrors, Config]]` back, where neither the `ApiClient` nor the configuration has been loaded. Instead, we have merely described how to load the configuration, and the `IO` has to be run to extract the configuration loading result.

[cats-effect]: https://github.com/typelevel/cats-effect
[ciris]: https://cir.is
[sbt]: https://www.scala-sbt.org
[kubernetes-java-client]: https://github.com/kubernetes-client/java
