package org.softnetwork.akka.http.config

import com.typesafe.config.{Config, ConfigFactory}

object Settings {

  lazy val config: Config = ConfigFactory.load()

  val Interface = config.getString("akka.http.server.interface")
  val Port      = config.getInt("akka.http.server.port")
}
