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

addCommandAlias("dct", ";dockerComposeTest") // navigate the projects

shellPrompt in ThisBuild := prompt

organization in ThisBuild := "org.softnetwork"

name := "akka-security-persistence"

version in ThisBuild := "0.3.1-SNAPSHOT"

scalaVersion in ThisBuild := "2.12.11"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-feature")

val jacksonExclusions = Seq(
  ExclusionRule(organization = "com.fasterxml.jackson.core"),
  ExclusionRule(organization = "org.codehaus.jackson")
)

/*val log4JExclusions = Seq(
  ExclusionRule(organization = "org.apache.logging.log4j"),
  ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12"),
  ExclusionRule(organization = "org.slf4j", name = "log4j-over-slf4j"),
  ExclusionRule(organization = "log4j", name = "log4j")
)*/

val nettyExclusions = Seq(
  ExclusionRule(organization = "io.netty")
)

val httpComponentsExclusions = Seq(
  ExclusionRule(organization = "org.apache.httpcomponents", name = "httpclient", configurations = Seq("test"))
)

val typesafeConfig = Seq(
  "com.typesafe"      % "config"   % Versions.typesafeConfig,
  "com.github.kxbmap" %% "configs" % Versions.kxbmap
)

val akka: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-actor" % Versions.akka,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-slf4j" % Versions.akka,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akka % Test
)

val akkaPersistence: Seq[ModuleID] = Seq(
  "com.typesafe.akka" %% "akka-persistence-typed" % Versions.akka,
  "com.typesafe.akka" %% "akka-persistence-query" % Versions.akka,
  "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.akka,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % Versions.akkaPersistenceJdbc excludeAll ExclusionRule(organization = "com.typesafe.akka"),
  "org.postgresql"       % "postgresql"  % Versions.postgresql
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

val scalatest = Seq(
  "org.scalatest"          %% "scalatest"  % Versions.scalatest  % Test,
  "com.github.tomakehurst" % "wiremock"    % Versions.wiremock   % Test exclude ("org.apache.httpcomponents", "httpclient"),
  "org.scalacheck"         %% "scalacheck" % Versions.scalacheck % Test
)

val wiremock = Seq(
  "com.github.tomakehurst" % "wiremock" % Versions.wiremock
)

val jackson = Seq(
  "com.fasterxml.jackson.core"   % "jackson-databind"          % Versions.jackson,
  "com.fasterxml.jackson.core"   % "jackson-core"              % Versions.jackson,
  "com.fasterxml.jackson.core"   % "jackson-annotations"       % Versions.jackson,
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % Versions.jackson
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
  "com.twitter" % "chill-akka_2.12" % Versions.chill excludeAll ExclusionRule(organization = "com.typesafe.akka")
)

val elastic = Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "com.sksamuel.elastic4s" %% "elastic4s-http"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
//  "com.sksamuel.elastic4s" %% "elastic4s-tcp"     % Versions.elastic4s exclude ("org.elasticsearch", "elasticsearch"),
  "org.elasticsearch"      % "elasticsearch"       % Versions.elasticSearch exclude ("org.apache.logging.log4j", "log4j-api"),
  "com.sksamuel.elastic4s" %% "elastic4s-testkit"  % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch") /*exclude ("org.apache.httpcomponents", "httpclient")*/,
  "com.sksamuel.elastic4s" %% "elastic4s-embedded" % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch") /*exclude ("org.apache.httpcomponents", "httpclient")*/,
  "com.sksamuel.elastic4s" %% "elastic4s-http"     % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch") /*exclude ("org.apache.httpcomponents", "httpclient")*/,
//  "com.sksamuel.elastic4s" %% "elastic4s-tcp"     % Versions.elastic4s % Test exclude ("org.elasticsearch", "elasticsearch") /*exclude ("org.apache.httpcomponents", "httpclient")*/,
  "org.elasticsearch"        % "elasticsearch"     % Versions.elasticSearch % Test exclude ("org.apache.logging.log4j", "log4j-api"),
  "org.apache.logging.log4j" % "log4j-api"         % Versions.log4j % Test,
  "org.apache.logging.log4j" % "log4j-slf4j-impl"  % Versions.log4j % Test,
  "org.apache.logging.log4j" % "log4j-core"        % Versions.log4j % Test
)/*.map(_.excludeAll(nettyExclusions: _*))*/

val jest = Seq(
  "io.searchbox" % "jest" % Versions.jest
).map(_.excludeAll(httpComponentsExclusions: _*))

val dockerTestKit = Seq(
  "com.whisk" %% "docker-testkit-scalatest"        % Versions.dockerTestKit % Test,
  "com.whisk" %% "docker-testkit-impl-docker-java" % Versions.dockerTestKit % Test exclude ("org.apache.httpcomponents", "httpclient"),
  "com.whisk" %% "docker-testkit-config"           % Versions.dockerTestKit % Test,
  "com.whisk" %% "docker-testkit-impl-spotify"     % Versions.dockerTestKit % Test
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
      json4s ++
      macwire ++
      typesafeConfig ++
      logging ++
      logback ++
      scalatest ++
      libphonenumber ++
      elastic ++
      jest ++
      dockerTestKit ++
      Seq(
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
        "commons-codec" % "commons-codec" % "1.10",
        "commons-io" % "commons-io" % "2.5",
        "org.apache.commons" % "commons-email" % "1.5",
        "com.github.fernandospr" % "javapns-jdk16" % "2.4.0",
        "com.google.gcm" % "gcm-server" % "1.0.0",
        "org.passay" % "passay" % "1.3.1",
        "com.chuusai" %% "shapeless" % "2.3.3",
        "org.apache.curator" % "curator-test" % "2.9.0" % Test,
        "org.apache.tika" % "tika-core" % "1.18",
        "com.mortennobel" % "java-image-scaling" % "0.8.6",
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",
        "com.mangopay" % "mangopay2-java-sdk" % "1.2.0",
        "commons-validator" % "commons-validator" % "1.6",
        "com.markatta" %% "akron" % "1.2" excludeAll(ExclusionRule(organization = "com.typesafe.akka"), ExclusionRule(organization = "org.scala-lang.modules")),
        "org.apache.commons" % "commons-text" % "1.4"
      )

dependencyOverrides in ThisBuild ++= jackson.toSet

parallelExecution in Test := false

val pbSettings = Seq(
  PB.targets in Compile := Seq(
    scalapb.gen() -> crossTarget.value / "protobuf_managed/main"
  )
)

lazy val common = project.in(file("common"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .enablePlugins(DockerComposePlugin, BuildInfoPlugin)

lazy val es = project.in(file("elastic"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val notification = project.in(file("notification"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .enablePlugins(DockerComposePlugin, BuildInfoPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val session = project.in(file("session"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(
    common % "compile->compile;test->test;it->it"
  )

lazy val security = project.in(file("security"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings, BuildInfoSettings.settings, pbSettings)
  .dependsOn(
    notification % "compile->compile;test->test,it->it"
  )
  .dependsOn(
    session % "compile->compile;test->test;it->it"
  )
  .dependsOn(
    es % "compile->compile;test->test"
  )
  .enablePlugins(DockerComposePlugin, BuildInfoPlugin)

lazy val root = project.in(file("."))
  .aggregate(
    common,
    es,
    notification,
    session,
    security
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)

envVars in Test := Map(
  "POSTGRES_USER" -> "admin",
  "POSTGRES_PASSWORD" -> "changeit",
  "POSTGRES_5432" -> "15432"
)
