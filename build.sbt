organization := "com.ovoenergy"
bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scalaVersion := "2.12.4"
crossScalaVersions := Seq("2.10.7", "2.11.12", scalaVersion.value)
releaseCrossBuild := true

libraryDependencies ++= Seq(
  "is.cir" %% "ciris-core" % "0.6.2",
  "io.kubernetes" % "client-java" % "0.2"
)
