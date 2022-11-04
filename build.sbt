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
  scalaVersion := "2.13.8",
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
  "io.kubernetes" % "client-java" % "16.0.2",
  "io.kubernetes" % "client-java-api" % "16.0.2",
  "is.cir" %% "ciris" % "2.3.2"
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
publishTo := Some("Artifactory Realm" at "https://kaluza.jfrog.io/artifactory/maven")
releaseCrossBuild := true
