package org.softnetwork.security.config

/**
  * Created by smanciot on 08/04/2018.
  */
import com.typesafe.config.{Config, ConfigFactory}

object Settings {

  lazy val config: Config = ConfigFactory.load()

  val Path = config.getString("akka.http.account.path")

  val BaseUrl = config.getString("security.baseUrl")

  val MailFrom = config.getString("security.mail.from")

  val ActivationTokenExpirationTime = config.getInt("security.activation.token.expirationTime")

  val VerificationCodeSize = config.getInt("security.verification.code.size")

  val VerificationCodeExpirationTime = config.getInt("security.verification.code.expirationTime")

}
