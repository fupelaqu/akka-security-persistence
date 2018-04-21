package org.softnetwork.notification.handlers

import java.util.{Date, UUID}

import org.softnetwork.notification.model.{NotificationStatusResult, NotificationStatus, NotificationAck, Notification}

/**
  * Created by smanciot on 14/04/2018.
  */
trait NotificationProvider[T<:Notification] {
  def send(notification: T): NotificationAck
  def ack(uuid: String, results: Seq[NotificationStatusResult] = Seq.empty): NotificationAck =
    NotificationAck(Some(uuid), results, new Date())
}

trait MockNotificationProvider[T<:Notification] extends NotificationProvider[T]{

  override def send(notification: T): NotificationAck = {
    NotificationAck(
      Some(UUID.randomUUID().toString),
      notification.to.map((recipient) => NotificationStatusResult(recipient, NotificationStatus.Sent, None)),
      new Date()
    )
  }

}