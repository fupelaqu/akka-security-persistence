package org.softnetwork.notification.handlers

/**
  * Created by smanciot on 07/04/2018.
  */

import java.util.Date

import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.notification.config.Settings
import org.softnetwork.notification.config.Settings.MailConfig
import org.softnetwork.notification.model._

import scala.util.{Failure, Success, Try}

/**
  * From https://gist.github.com/mariussoutier/3436111
  */
class MailProvider extends NotificationProvider[Mail] with StrictLogging {

  val mailConfig: MailConfig = Settings.config.get.mail

  def send(notification: Mail): NotificationAck = {
    import org.apache.commons.mail._

    val format =
      if (notification.attachment.isDefined) MultiPart
      else if (notification.richMessage.isDefined) Rich
      else Plain

    val commonsMail: Email = format match {
      case Plain     => new SimpleEmail().setMsg(notification.message)
      case Rich      => new HtmlEmail().setHtmlMsg(notification.richMessage.get).setTextMsg(notification.message)
      case MultiPart =>
        val emailAttachment = new EmailAttachment()
        emailAttachment.setPath(notification.attachment.get.file.getAbsolutePath)
        emailAttachment.setDisposition(EmailAttachment.ATTACHMENT)
        emailAttachment.setName(notification.attachment.get.name)
        new MultiPartEmail().attach(emailAttachment).setMsg(notification.message)
    }

    // Set authentication
    commonsMail.setHostName(mailConfig.host)
    commonsMail.setSmtpPort(mailConfig.port)
    commonsMail.setSslSmtpPort(mailConfig.sslPort.toString)
    if (mailConfig.username.length > 0) {
      commonsMail.setAuthenticator(new DefaultAuthenticator(mailConfig.username, mailConfig.password))
    }
    commonsMail.setSSLOnConnect(mailConfig.sslEnabled)
    commonsMail.setSSLCheckServerIdentity(mailConfig.sslCheckServerIdentity)
    commonsMail.setStartTLSEnabled(mailConfig.startTLSEnabled)

    // Can't add these via fluent API because it produces exceptions
    notification.to.foreach(commonsMail.addTo)
    notification.cc.foreach(commonsMail.addCc)
    notification.bcc.foreach(commonsMail.addBcc)

    Try(commonsMail
      .setFrom(notification.from._1, notification.from._2.getOrElse(""))
      .setSubject(notification.subject)
      .send()) match {
      case Success(s) =>
        logger.info(s)
        NotificationAck(
          Some(s),
          notification.to.map((recipient) => (recipient, NotificationStatus.Sent)),
          new Date()
        )
      case Failure(f) =>
        logger.error(f.getMessage, f)
        NotificationAck(
          None,
          notification.to.map((recipient) => (recipient, NotificationStatus.Undelivered)),
          new Date()
        )
    }
  }

}

class MockMailProvider extends MailProvider with MockNotificationProvider[Mail]
