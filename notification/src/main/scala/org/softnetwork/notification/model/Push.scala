package org.softnetwork.notification.model

import java.util.Date

import org.softnetwork.notification.model.Platform.Platform

/**
  * Created by smanciot on 16/04/2018.
  */
/**
  *
  * @param from - application id
  * @param subject - notification subject
  * @param message - notification message
  * @param maxTries - maximum number of attempts allowed to send this notification
  * @param nbTries - number of attempts made to send this notification
  * @param deferred - notification should be sent as of the specified date
  * @param ackUuid - notification acknowledgment id
  * @param status - notification status
  * @param lastUpdated - notification last updated date
  * @param devices - devices
  * @param id - push id
  * @param badge - push badge
  * @param sound - the sound to be played on the device when the notification will be received
  */
case class Push(override val from: (String, Option[String]) = ("", None),
                override val subject: String,
                override val message: String,
                override val maxTries: Int = 1,
                override val nbTries: Int = 0,
                override val deferred: Option[Date] = None,
                override val ackUuid: Option[String] = None,
                override val status: NotificationStatus.Value = NotificationStatus.Pending,
                override val results: Seq[NotificationStatusResult] = Seq.empty,
                override val lastUpdated: Option[Date] = None,
                devices: Seq[BasicDevice],
                id: String,
                badge: Int = 0,
                sound: Option[String] = None) extends Notification {

  override val to: Seq[String] = devices.map(_.regId)

  override val `type`: NotificationType.Value = NotificationType.Push

  override def incNbTries(): Notification = copy(nbTries = nbTries + 1)

  override def copyWithAck(ack: NotificationAck): Notification = copy(
    ackUuid = ack.uuid,
    status = ack.status,
    results = ack.results,
    lastUpdated = Some(ack.date)
  )

}

case class BasicDevice(regId: String, platform: Platform)