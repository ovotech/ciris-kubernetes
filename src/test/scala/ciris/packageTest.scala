package ciris

import cats.effect.IO
import cats.implicits._
import ciris.kubernetes._
import munit.CatsEffectSuite
import cats.effect.Blocker

class packageTest extends CatsEffectSuite {
  test("secrets") {

    final case class Config(
      appName: String,
      apiKey: Secret[String],
      username: String,
      timeout: Int
    )

    Blocker[IO].use { blocker =>
      val configValue = secretInNamespace("secrets-test", blocker).flatMap { secret =>
        (
          secret("apikey").secret,
          secret("username"),
          secret("defaults", "timeout").as[Int]
        ).parMapN { (apiKey, username, timeout) =>
          Config(
            appName = "my-api",
            apiKey = apiKey,
            username = username,
            timeout = timeout
          )
        }
      }

      configValue.load[IO].map { config =>
        val expected = Config("my-api", Secret("dummykey"), "dummyuser", 10)
        assertEquals(config, expected)
      }
    }
  }
  test("configmaps") {
    final case class Config(
      appName: String,
      pizzaBrand: String,
      deliveryRadius: Int,
      isDeliveryCharge: Boolean
    )

    Blocker[IO].use { blocker =>
      val config = configMapInNamespace("pizza", blocker).flatMap { configMap =>
        (
          configMap("pizzabrand"), // Key can be omitted if config map has only one entry
          configMap("delivery", "radius")
            .as[Int], // Key is necessary if config map has multiple entries
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

      config.load[IO].map { config =>
        val expected = Config("my-pizza-api", "domino", 5, true)
        assertEquals(config, expected)
      }
    }
  }
  test("missing secret") {
    val namespace = "secrets-test"
    val secretName = "missing"
    intercept[ConfigException] {
      Blocker[IO]
        .use { blocker =>
          secretInNamespace(namespace, blocker)
            .flatMap { secret =>
              secret(secretName).as[String]
            }
            .load[IO]
        }
        .unsafeRunSync()
    }
  }

  test("missing secret key") {
    val namespace = "secrets-test"
    val secretName = "secrets-test"
    val secretKey = "missing-key"

    intercept[ConfigException] {
      Blocker[IO]
        .use { blocker =>
          secretInNamespace(namespace, blocker)
            .flatMap { secret =>
              secret(secretName, secretKey).as[String]
            }
            .load[IO]
        }
        .unsafeRunSync()
    }
  }

  test("missing configmap") {
    val namespace = "pizza"
    val configMapName = "missingmissing"

    intercept[ConfigException] {
      Blocker[IO]
        .use { blocker =>
          configMapInNamespace(namespace, blocker)
            .flatMap { configMap =>
              configMap(configMapName).as[String]
            }
            .load[IO]
        }
        .unsafeRunSync()
    }
  }
  test("missing configmap key") {
    val namespace = "pizza"
    val configMapName = "delivery"
    val configMapKey = "missing-key"

    intercept[ConfigException] {
      Blocker[IO]
        .use { blocker =>
          configMapInNamespace(namespace, blocker)
            .flatMap { configMap =>
              configMap(configMapName, configMapKey).as[String]
            }
            .load[IO]
        }
        .unsafeRunSync()
    }
  }
}
