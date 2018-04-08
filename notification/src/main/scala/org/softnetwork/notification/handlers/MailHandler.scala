package org.softnetwork.notification.handlers

/**
  * Created by smanciot on 07/04/2018.
  */
import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.notification.config.Settings.MailConfig
import org.softnetwork.notification.model._

import scala.util.{Failure, Success, Try}

/**
  * From https://gist.github.com/mariussoutier/3436111
  */
trait MailHandler extends StrictLogging {
  def send(mail: Mail)(implicit mailConfig: MailConfig): Boolean = {
    import org.apache.commons.mail._

    val format =
      if (mail.attachment.isDefined) MultiPart
      else if (mail.richMessage.isDefined) Rich
      else Plain

    val commonsMail: Email = format match {
      case Plain     => new SimpleEmail().setMsg(mail.message)
      case Rich      => new HtmlEmail().setHtmlMsg(mail.richMessage.get).setTextMsg(mail.message)
      case MultiPart =>
        val emailAttachment = new EmailAttachment()
        emailAttachment.setPath(mail.attachment.get.file.getAbsolutePath)
        emailAttachment.setDisposition(EmailAttachment.ATTACHMENT)
        emailAttachment.setName(mail.attachment.get.name)
        new MultiPartEmail().attach(emailAttachment).setMsg(mail.message)
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
    mail.to.foreach(commonsMail.addTo)
    mail.cc.foreach(commonsMail.addCc)
    mail.bcc.foreach(commonsMail.addBcc)

    Try(commonsMail.setFrom(mail.from._1, mail.from._2).setSubject(mail.subject).send()) match {
      case Success(s) =>
        logger.info(s)
        true
      case Failure(f) =>
        logger.error(f.getMessage, f)
        false
    }
  }
}

trait MockMailHandler extends MailHandler {
  override def send(mail: Mail)(implicit mailConfig: MailConfig): Boolean = true
}
