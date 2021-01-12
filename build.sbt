organization := "com.ovoenergy"
bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scalaVersion := "2.13.5"
crossScalaVersions := Seq(scalaVersion.value, "2.12.13  ")
releaseCrossBuild := true

libraryDependencies ++= Seq(
  "is.cir" %% "ciris" % "1.2.1",
  "io.kubernetes" % "client-java" % "11.0.0",
  "org.scalameta" %% "munit" % "0.7.20" % Test,
  "org.typelevel" %% "cats-core" % "2.4.2" % Test,
  "org.typelevel" %% "cats-effect" % "2.3.3" % Test
)

testFrameworks += new TestFramework("munit.Framework")
