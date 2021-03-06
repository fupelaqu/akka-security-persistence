package org.softnetwork.session.config

/**
  * Created by smanciot on 21/03/2018.
  */
import com.typesafe.config.{Config, ConfigFactory}
import configs.ConfigError

object Settings {
  lazy val config: Config = ConfigFactory.load()

  def configErrorsToException(err: ConfigError) =
    new IllegalStateException(err.entries.map(_.messageWithPath).mkString(","))

  object Session {
    val CookieName = config getString "akka.http.session.cookie.name"

    val CookieSecret = config getString "akka.http.session.server-secret"

    require(CookieName.nonEmpty, "akka.http.session.cookie.name must be non-empty")
    require(CookieSecret.nonEmpty, "akka.http.session.server-secret must be non-empty")
  }
}
