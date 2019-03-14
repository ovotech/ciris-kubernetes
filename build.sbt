organization := "com.ovoenergy"
bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scalaVersion := "2.12.7"
crossScalaVersions := Seq("2.11.12", scalaVersion.value)
releaseCrossBuild := true

scalacOptions += "-language:higherKinds"

val cirisVersion = "0.12.1"

libraryDependencies ++= Seq(
  "is.cir" %% "ciris-core" % cirisVersion,
  "is.cir" %% "ciris-cats-effect" % cirisVersion,
  "io.kubernetes" % "client-java" % "3.0.0"
)
