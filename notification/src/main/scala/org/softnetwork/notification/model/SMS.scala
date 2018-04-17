package org.softnetwork.notification.model

import java.util.Date

/**
  * Created by smanciot on 14/04/2018.
  */
case class SMS(override val from: (String, Option[String]),
                override val to: Seq[String],
                override val subject: String,
                override val message: String,
                override val maxTries: Int = 1,
                override val nbTries: Int = 0,
                override val deferred: Option[Date] = None,
                override val ackUuid: Option[String] = None,
                override val status: NotificationStatus.Value = NotificationStatus.Pending,
                override val lastUpdated: Option[Date] = None) extends Notification {

  override val `type`: NotificationType.Value = NotificationType.SMS

  override def incNbTries(): Notification = copy(nbTries = nbTries + 1)

  override def copyWithAck(ack: NotificationAck): Notification = copy(
    ackUuid = ack.uuid,
    status = ack.status,
    lastUpdated = Some(ack.date)
  )

}
