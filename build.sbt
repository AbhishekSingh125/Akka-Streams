name := "akka-streams"

version := "0.1"

scalaVersion := "2.13.6"

lazy val akkaVersion = "2.6.15"
lazy val scalaTestVersion = "3.2.9"

resolvers += "Artima Maven repository" at "https://repo.artima.com/releases"

libraryDependencies ++= Seq(
  "com.typesafe.akka"  %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion
)