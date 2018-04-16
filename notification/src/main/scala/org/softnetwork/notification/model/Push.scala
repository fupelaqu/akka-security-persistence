package org.softnetwork.notification.model

import java.util.Date

import org.softnetwork.notification.model.Platform.Platform

/**
  * Created by smanciot on 16/04/2018.
  */
/**
  *
  * @param from - application id
  * @param to - registration id(s)
  * @param subject - notification subject
  * @param message - notification message
  * @param maxTries - maximum number of attempts allowed to send this notification
  * @param nbTries - number of attempts made to send this notification
  * @param deferred - notification should be sent as of the specified date
  * @param ackUuid - notification acknowledgment id
  * @param status - notification status
  * @param lastUpdated - notification last updated date
  * @param platform - device platform
  * @param uuid - push id
  * @param badge - push badge
  * @param sound - the sound to be played on the device when the notification will be received
  */
case class Push(override val from: (String, Option[String]),
           override val to: Seq[String],
           override val subject: String,
           override val message: String,
           override val maxTries: Int = 1,
           override val nbTries: Int = 0,
           override val deferred: Option[Date] = None,
           override val ackUuid: Option[String] = None,
           override val status: NotificationStatus.Value = NotificationStatus.Pending,
           override val lastUpdated: Option[Date] = None,
           platform: Platform,
           uuid: String,
           badge: Option[Long] = None,
           sound: Option[String] = None) extends Notification {

  override val `type`: NotificationType.Value = NotificationType.Push

  override def incNbTries(): Notification = copy(nbTries = nbTries + 1)

  override def copyWithAck(ack: NotificationAck): Notification = copy(
    ackUuid = ack.uuid,
    status = ack.status,
    lastUpdated = Some(ack.date)
  )

}


