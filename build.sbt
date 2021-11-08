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
  crossScalaVersions := Seq(scalaVersion.value, "2.12.14")
)

libraryDependencies ++= Seq(
  "is.cir" %% "ciris" % "1.2.1",
  "io.kubernetes" % "client-java" % "13.0.1"
)

licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
publishTo := Some("Artifactory Realm" at "https://kaluza.jfrog.io/artifactory/maven")
releaseCrossBuild := true
