package org.softnetwork.notification.config

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import configs.Configs

object Settings extends StrictLogging {

  lazy val config: Option[NotificationConfig] = Configs[NotificationConfig].get(ConfigFactory.load(), "notification").toEither match {
    case Left(configError) =>
      logger.error(s"Something went wrong with the provided arguments $configError")
      None
    case Right(r) => Some(r)
  }

  case class NotificationConfig(mail: MailConfig, push: PushConfig, sms : SMSConfig)

  case class MailConfig(host: String,
                        port: Int,
                        sslPort: Int,
                        username: String,
                        password: String,
                        sslEnabled: Boolean,
                        sslCheckServerIdentity: Boolean,
                        startTLSEnabled: Boolean)

  case class PushConfig(apns: ApnsConfig, gcm: GcmConfig)

  case class ApnsConfig(keystore: Keystore, dryRun: Boolean = false)

  case class Keystore(path: String, password: String = "")

  case class GcmConfig(apiKey: String)

  case class SMSConfig(mode: Option[SMSMode.Config] = None)
}
