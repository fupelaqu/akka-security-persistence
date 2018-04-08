package org.softnetwork.notification.config

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

object Settings extends StrictLogging {

  lazy val config: Option[MailConfig] = Configs[MailConfig].get(ConfigFactory.load(), "mail.smtp").toEither match {
    case Left(configError) =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      None
    case Right(r) => Some(r)
  }

  case class MailConfig(host: String,
                        port: Int,
                        sslPort: Int,
                        username: String,
                        password: String,
                        sslEnabled: Boolean,
                        sslCheckServerIdentity: Boolean,
                        startTLSEnabled: Boolean)

}
