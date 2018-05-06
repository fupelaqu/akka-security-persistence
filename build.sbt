import sbt.Resolver

import Common._
import org.softnetwork.sbt.build._

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

organization := "org.softnetwork.security"

name := "akka-security-persistence"

version := "0.1"

scalaVersion in ThisBuild := "2.11.12"

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
  "com.typesafe"      % "config"   % Versions.typesafeConfig,
  "com.github.kxbmap" %% "configs" % Versions.kxbmap
)

val akka: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-actor" % Versions.akka,
  "com.typesafe.akka" %% "akka-remote" % Versions.akka,
  "com.typesafe.akka" %% "akka-testkit" % Versions.akka % Test
)

val akkaPersistence: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-persistence" % Versions.akka,
  "com.github.krasserm" %% "akka-persistence-kafka" % Versions.akkaPersistenceKafka
)

val akkaStream: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-stream" % Versions.akka,
  "com.typesafe.akka" %% "akka-stream-testkit" % Versions.akka % Test
)

val akkaHttp: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
  "de.heikoseeberger" %% "akka-http-json4s" % Versions.akkaHttpJson4s,
  "com.softwaremill.akka-http-session" %% "core" % Versions.akkaHttpSession,
  "com.softwaremill.akka-http-session" %% "jwt"  % Versions.akkaHttpSession,
  "com.typesafe.akka" %% "akka-http-testkit" % Versions.akkaHttp % Test
)

val kafka: Seq[ModuleID] = Seq(
  "org.apache.kafka" %% "kafka"        % Versions.kafka,
  "org.apache.kafka" % "kafka-clients" % Versions.kafka,
  "org.apache.kafka" %% "kafka"        % Versions.kafka % "test" classifier "test",
  "org.apache.kafka" % "kafka-clients" % Versions.kafka % "test" classifier "test"
).map(_.excludeAll(log4JExclusions: _*))

val kafkaClient: Seq[ModuleID] = kafka ++ Seq(
  "net.cakesolutions" %% "scala-kafka-client"         % Versions.scalaKafkaClient,
  "net.cakesolutions" %% "scala-kafka-client-testkit" % Versions.scalaKafkaClient % "test"
).map(_.excludeAll(log4JExclusions: _*))

val kafkaStreams: Seq[ModuleID] = kafka ++ Seq(
  "org.apache.kafka" % "kafka-streams"           % Versions.kafka,
  "org.apache.kafka" % "kafka-streams"           % Versions.kafka % "test" classifier "test",
  "fr.psug.kafka"    %% "typesafe-kafka-streams" % Versions.typesafeKafkaStreams
).map(_.excludeAll(log4JExclusions: _*))

val scalatest = Seq(
  "org.scalatest"          %% "scalatest"  % Versions.scalatest  % "it, test",
  "com.github.tomakehurst" % "wiremock"    % Versions.wiremock   % "test" exclude ("org.apache.httpcomponents", "httpclient"),
  "org.scalacheck"         %% "scalacheck" % Versions.scalacheck % "test"
)

val wiremock = Seq(
  "com.github.tomakehurst" % "wiremock" % Versions.wiremock
)

val jackson = Seq(
  "com.fasterxml.jackson.core"   % "jackson-databind"          % Versions.jackson,
  "com.fasterxml.jackson.core"   % "jackson-core"              % Versions.jackson,
  "com.fasterxml.jackson.core"   % "jackson-annotations"       % Versions.jackson,
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % Versions.jackson
)

val json4s = Seq(
  "org.json4s" %% "json4s-jackson" % Versions.json4s,
  "org.json4s" %% "json4s-ext"     % Versions.json4s
).map(_.excludeAll(jacksonExclusions: _*)) ++ jackson

val macwire = Seq(
  "com.softwaremill.macwire" %% "macros" % Versions.macwire % "provided",
  "com.softwaremill.macwire" %% "util"   % Versions.macwire,
  "com.softwaremill.macwire" %% "proxy"  % Versions.macwire
)

val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % Versions.scalaLogging,
  "org.log4s"                  %% "log4s"         % Versions.log4s,
  "org.slf4j"                  % "slf4j-api"      % Versions.slf4j,
  "org.slf4j"                  % "jcl-over-slf4j" % Versions.slf4j,
  "org.slf4j"                  % "jul-to-slf4j"   % Versions.slf4j
)

val logback = Seq(
  "ch.qos.logback" % "logback-classic"  % Versions.logback,
  "org.slf4j"      % "log4j-over-slf4j" % Versions.slf4j
)

val libphonenumber = Seq(
  "com.googlecode.libphonenumber" % "libphonenumber" % "8.9.2",
  "com.googlecode.libphonenumber" % "geocoder" % "2.91"
)

val kryo = Seq(
  "com.twitter" %% "chill-bijection" % Versions.chill
)

val chill_akka = Seq(
  "com.twitter" % "chill-akka_2.11" % Versions.chill
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
        "com.github.fernandospr" % "javapns-jdk16" % "2.4.0",
        "com.google.gcm" % "gcm-server" % "1.0.0",
        "org.passay" % "passay" % "1.3.1",
        "com.chuusai" %% "shapeless" % "2.3.3",
        "org.apache.curator" % "curator-test" % "2.9.0" % "test"
      )

dependencyOverrides in ThisBuild ++= jackson.toSet

parallelExecution in Test := false

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .enablePlugins(DockerComposePlugin)

lazy val notification = project.in(file("notification"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .enablePlugins(DockerComposePlugin)
  .dependsOn(common % "compile->compile;test->test;it->it")

lazy val session = project.in(file("session"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val security = project.in(file("security"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(
    notification % "compile->compile;test->test,it->it"
  )
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .enablePlugins(DockerComposePlugin, sbtdocker.DockerPlugin, JavaAppPackaging)

lazy val root = project.in(file("."))
  .aggregate(
    common,
    notification,
    session,
    security
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)