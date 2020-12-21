organization := "com.ovoenergy"
bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scalaVersion := "2.13.3"
crossScalaVersions := Seq(scalaVersion.value, "2.12.12")
releaseCrossBuild := true

libraryDependencies ++= Seq(
  "is.cir" %% "ciris" % "1.2.1",
  "io.kubernetes" % "client-java" % "10.0.1"
)
