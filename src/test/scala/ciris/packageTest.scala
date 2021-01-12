package ciris

import cats.effect.ContextShift
import cats.effect.internals.IOContextShift

import scala.concurrent.ExecutionContext.global

class packageTest extends munit.FunSuite {
  test("secrets") {
    import cats.effect.{Blocker, ExitCode, IO}
    import cats.implicits._
    import ciris.kubernetes._
    import ciris.Secret

    final case class Config(
      appName: String,
      apiKey: Secret[String],
      username: String,
      timeout: Int
    )

    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    val computed = Blocker[IO].use { blocker =>
      val config =
        secretInNamespace("secrets-test", blocker).flatMap { secret =>
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

      config.load[IO]
    }.unsafeRunSync()

    val expected = Config("my-api", Secret("dummykey"), "dummyuser", 10)

    assertEquals(computed, expected)
  }
}
