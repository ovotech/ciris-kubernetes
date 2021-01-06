organization := "com.ovoenergy"
bintrayOrganization := Some("ovotech")
licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

scalaVersion := "2.13.4"
crossScalaVersions := Seq(scalaVersion.value, "2.12.12")
releaseCrossBuild := true

libraryDependencies ++= Seq(
  "is.cir" %% "ciris" % "1.2.1",
  "io.kubernetes" % "client-java" % "11.0.0"
)

dependencyOverrides ++= Seq(
  "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.68",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.68"
)
