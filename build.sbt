import sbt.Resolver

import Common._

/////////////////////////////////
// Defaults
/////////////////////////////////

org.softnetwork.sbt.build.Publication.settings

/////////////////////////////////
// Useful aliases
/////////////////////////////////

addCommandAlias("cd", "project") // navigate the projects

addCommandAlias("cc", ";clean;compile") // clean and compile

addCommandAlias("pl", ";clean;publishLocal") // clean and publish locally

addCommandAlias("pr", ";clean;publish") // clean and publish globally

addCommandAlias("pld", ";clean;local:publishLocal;dockerComposeUp") // clean and publish/launch the docker environment

(shellPrompt in ThisBuild) := prompt

name := "akka-security-persistence"

version := "0.1"

scalaVersion in ThisBuild := "2.11.12"

val akkaVersion = "2.5.11"

val akkaHttpVersion = "10.0.11"

val akkaHttpJson4sVersion = "1.7.0" // 1.20.0 ?

val akkaHttpSessionVersion = "0.5.3"

val akkaPersistenceKafkaVersion = "0.6"

val kafkaVersion = "1.0.0"

val scalaKafkaClientVersion = "1.0.0"

val typesafeKafkaStreamsVersion = "0.2.1"

val wiremockVersion = "2.14.0"

val scalatestVersion = "3.0.1"

val scalacheckVersion = "1.13.4"

val typesafeConfigVersion = "1.2.1"

val kxbmapVersion = "0.4.3"

val jacksonVersion = "2.8.4"

val json4sVersion = "3.2.11"

val macwireVersion = "2.2.3"

val scalaLoggingVersion = "3.4.0"

val logbackVersion = "1.1.7"

val slf4jVersion = "1.7.21"

val log4sVersion = "1.3.3"

val chillVersion = "0.9.2"

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "org.codehaus.jackson")
)

val log4JExclusions = Seq(
  ExclusionRule(organization = "org.apache.logging.log4j"),
  ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"),
  ExclusionRule(name = "log4j-over-slf4j"),
  ExclusionRule(organization = "log4j", name = "log4j")
)

val typesafeConfig = Seq(
  "com.typesafe"      % "config"   % typesafeConfigVersion,
  "com.github.kxbmap" %% "configs" % kxbmapVersion
)

val akka: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
)

val akkaPersistence: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.github.krasserm" %% "akka-persistence-kafka" % akkaPersistenceKafkaVersion
)

val akkaStream: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
)

val akkaHttp: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "de.heikoseeberger" %% "akka-http-json4s" % akkaHttpJson4sVersion,
  "com.softwaremill.akka-http-session" %% "core" % akkaHttpSessionVersion,
  "com.softwaremill.akka-http-session" %% "jwt"  % akkaHttpSessionVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
)

val kafka: Seq[ModuleID] = Seq(
  "org.apache.kafka" %% "kafka"        % kafkaVersion,
  "org.apache.kafka" % "kafka-clients" % kafkaVersion,
  "org.apache.kafka" %% "kafka"        % kafkaVersion % "test" classifier "test",
  "org.apache.kafka" % "kafka-clients" % kafkaVersion % "test" classifier "test"
).map(_.excludeAll(log4JExclusions: _*))

val kafkaClient: Seq[ModuleID] = kafka ++ Seq(
  "net.cakesolutions" %% "scala-kafka-client"         % scalaKafkaClientVersion,
  "net.cakesolutions" %% "scala-kafka-client-testkit" % scalaKafkaClientVersion % "test"
).map(_.excludeAll(log4JExclusions: _*))

val kafkaStreams: Seq[ModuleID] = kafka ++ Seq(
  "org.apache.kafka" % "kafka-streams"           % kafkaVersion,
  "org.apache.kafka" % "kafka-streams"           % kafkaVersion % "test" classifier "test",
  "fr.psug.kafka"    %% "typesafe-kafka-streams" % typesafeKafkaStreamsVersion
).map(_.excludeAll(log4JExclusions: _*))

val scalatest = Seq(
  "org.scalatest"          %% "scalatest"  % scalatestVersion  % "test",
  "com.github.tomakehurst" % "wiremock"    % wiremockVersion   % "test" exclude ("org.apache.httpcomponents", "httpclient"),
  "org.scalacheck"         %% "scalacheck" % scalacheckVersion % "test"
)

val wiremock = Seq(
  "com.github.tomakehurst" % "wiremock" % wiremockVersion
)

val jackson = Seq(
  "com.fasterxml.jackson.core"   % "jackson-databind"          % jacksonVersion,
  "com.fasterxml.jackson.core"   % "jackson-core"              % jacksonVersion,
  "com.fasterxml.jackson.core"   % "jackson-annotations"       % jacksonVersion,
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % jacksonVersion
)

val json4s = Seq(
  "org.json4s" %% "json4s-jackson" % json4sVersion,
  "org.json4s" %% "json4s-ext"     % json4sVersion
).map(_.excludeAll(jacksonExclusions: _*)) ++ jackson

val macwire = Seq(
  "com.softwaremill.macwire" %% "macros" % macwireVersion % "provided",
  "com.softwaremill.macwire" %% "util"   % macwireVersion,
  "com.softwaremill.macwire" %% "proxy"  % macwireVersion
)

val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.log4s"                  %% "log4s"         % log4sVersion,
  "org.slf4j"                  % "slf4j-api"      % slf4jVersion,
  "org.slf4j"                  % "jcl-over-slf4j" % slf4jVersion,
  "org.slf4j"                  % "jul-to-slf4j"   % slf4jVersion
)

val logback = Seq(
  "ch.qos.logback" % "logback-classic"  % logbackVersion,
  "org.slf4j"      % "log4j-over-slf4j" % slf4jVersion
)

val libphonenumber = Seq(
  "com.googlecode.libphonenumber" % "libphonenumber" % "8.9.2",
  "com.googlecode.libphonenumber" % "geocoder" % "2.91"
)

val kryo = Seq(
  "com.twitter" %% "chill-bijection" % chillVersion
)

val chill_akka = Seq(
  "com.twitter" % "chill-akka_2.11" % chillVersion
)

resolvers in ThisBuild ++= Seq(
  Resolver.bintrayRepo("cakesolutions", "maven"),
  Resolver.bintrayRepo("hseeberger", "maven"),
  Resolver.sonatypeRepo("releases"),
  "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
)

libraryDependencies in ThisBuild ++=
      akka ++
      kryo ++
      chill_akka ++
      akkaPersistence ++
      akkaHttp ++
      kafka ++
      kafkaClient ++
      kafkaStreams ++
      json4s ++
      macwire ++
      typesafeConfig ++
      logging ++
      logback ++
      scalatest ++
      libphonenumber ++
      Seq(
        "commons-codec" % "commons-codec" % "1.8",
        "org.apache.commons" % "commons-email" % "1.5",
        "org.passay" % "passay" % "1.3.1",
        "com.chuusai" %% "shapeless" % "2.3.3",
        "org.apache.curator" % "curator-test" % "2.9.0" % "test"
      )

dependencyOverrides in ThisBuild ++= jackson.toSet

parallelExecution in Test := false

lazy val common = project.in(file("common"))

lazy val notification = project.in(file("notification")).dependsOn(common % "compile->compile;test->test")

lazy val session = project.in(file("session"))
  .dependsOn(
    common % "compile->compile;test->test"
  )

lazy val security = project.in(file("security"))
  .dependsOn(
    notification % "compile->compile;test->test"
  )
  .dependsOn(
    session % "compile->compile;test->test"
  )
