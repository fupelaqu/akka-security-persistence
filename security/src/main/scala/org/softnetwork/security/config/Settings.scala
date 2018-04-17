package org.softnetwork.security.config

/**
  * Created by smanciot on 08/04/2018.
  */
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import configs.Configs
import Password._

object Settings extends StrictLogging {

  lazy val config: Config = ConfigFactory.load()

  val Path = config.getString("akka.http.account.path")

  val BaseUrl = config.getString("security.baseUrl")

  val MailFrom = config.getString("security.mail.from")

  val ActivationTokenExpirationTime = config.getInt("security.activation.token.expirationTime")

  val VerificationCodeSize = config.getInt("security.verification.code.size")

  val VerificationCodeExpirationTime = config.getInt("security.verification.code.expirationTime")

  val ApplicationId = config.getString("security.applicationId")

  def passwordRules(config: Config = config) = Configs[PasswordRules].get(config, "security.password").toEither match{
    case Left(configError)  =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      PasswordRules()
    case Right(rules) => rules
  }

}

