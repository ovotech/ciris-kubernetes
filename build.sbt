lazy val root = project
  .in(file("."))
  .settings(
    moduleName := "ciris-kubernetes",
    name := moduleName.value,
    scalaSettings,
    metadataSettings
  )

lazy val metadataSettings = Seq(
  organization := "com.ovoenergy",
  organizationName := "OVO Energy",
  organizationHomepage := Some(url("https://www.ovoenergy.com"))
)

lazy val scalaSettings = Seq(
  scalaVersion := "2.13.7",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.15"),
  scalacOptions ++= {
    val commonScalacOptions =
      Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked",
        // "-Xfatal-warnings",
        "-language:higherKinds",
        "-Xlint",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ywarn-value-discard",
        "-Ywarn-unused",
        "-Xlint:unused"
      )

    val scala212ScalacOptions =
      if (scalaVersion.value.startsWith("2.12")) {
        Seq("-Yno-adapted-args", "-Ypartial-unification")
      } else Seq()

    commonScalacOptions ++
      scala212ScalacOptions
  },
  Compile / console / scalacOptions --= Seq("-Xlint", "-Ywarn-unused")
)

libraryDependencies ++= Seq(
  "io.kubernetes" % "client-java" % "13.0.1",
  "io.kubernetes" % "client-java-api" % "13.0.1",
  "is.cir" %% "ciris" % "2.1.1"
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
publishTo := Some("Artifactory Realm" at "https://kaluza.jfrog.io/artifactory/maven")
releaseCrossBuild := true
