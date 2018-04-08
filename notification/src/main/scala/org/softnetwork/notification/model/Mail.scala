package org.softnetwork.notification.model

/**
  * Created by smanciot on 07/04/2018.
  */
case class Mail(from: (String, String), // (email -> name)
                to: Seq[String],
                cc: Seq[String] = Seq.empty,
                bcc: Seq[String] = Seq.empty,
                subject: String,
                message: String,
                richMessage: Option[String] = None,
                attachment: Option[Attachment] = None)

sealed abstract class MailType

case object Plain extends MailType

case object Rich extends MailType

case object MultiPart extends MailType

case class Attachment(file: (java.io.File), name: String)

