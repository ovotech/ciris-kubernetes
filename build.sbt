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
  scalaVersion := "2.13.5",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.13")
)

libraryDependencies ++= Seq(
  "io.kubernetes" % "client-java" % "12.0.0",
  "io.kubernetes" % "client-java-api" % "12.0.0",
  "is.cir" %% "ciris" % "1.2.1",
  "org.typelevel" %% "cats-core" % "2.1.1",
  "org.typelevel" %% "cats-effect" % "2.1.4"
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
publishTo := Some("Artifactory Realm" at "https://kaluza.jfrog.io/artifactory/maven")
releaseCrossBuild := true
