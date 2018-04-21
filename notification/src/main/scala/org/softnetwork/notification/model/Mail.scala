package org.softnetwork.notification.model

import java.util.{Date, UUID}

/**
  * Created by smanciot on 07/04/2018.
  */
case class Mail(override val from: (String, Option[String]), // (email -> name)
                override val to: Seq[String],
                cc: Seq[String] = Seq.empty,
                bcc: Seq[String] = Seq.empty,
                override val subject: String,
                override val message: String,
                richMessage: Option[String] = None,
                attachment: Option[Attachment] = None,
                override val maxTries: Int = 1,
                override val nbTries: Int = 0,
                override val deferred: Option[Date] = None,
                override val ackUuid: Option[String] = None,
                override val status: NotificationStatus.Value = NotificationStatus.Pending,
                override val results: Seq[NotificationStatusResult] = Seq.empty,
                override val lastUpdated: Option[Date] = None) extends Notification {

  override val `type`: NotificationType.Value = NotificationType.Mail

  override def incNbTries(): Notification = copy(nbTries = nbTries + 1)

  override def copyWithAck(ack: NotificationAck): Notification = copy(
    ackUuid = ack.uuid,
    status = ack.status,
    results = ack.results,
    lastUpdated = Some(ack.date)
  )

}

sealed abstract class MailType

case object Plain extends MailType

case object Rich extends MailType

case object MultiPart extends MailType

case class Attachment(file: (java.io.File), name: String)

