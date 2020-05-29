organization := "com.ovoenergy"
bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scalaVersion := "2.13.1"
crossScalaVersions := Seq(scalaVersion.value, "2.12.10")
releaseCrossBuild := true

libraryDependencies ++= Seq(
  "is.cir" %% "ciris" % "1.0.4",
  "io.kubernetes" % "client-java" % "8.0.2"
)
