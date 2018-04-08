package org.softnetwork.security.config

/**
  * Created by smanciot on 08/04/2018.
  */
import com.typesafe.config.{Config, ConfigFactory}

object Settings {

  lazy val config: Config = ConfigFactory.load()

  val Path = config.getString("akka.http.account.path")
}
