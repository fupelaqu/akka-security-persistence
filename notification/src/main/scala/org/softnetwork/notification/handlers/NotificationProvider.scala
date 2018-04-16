package org.softnetwork.notification.handlers

import org.softnetwork.notification.model.NotificationStatus.NotificationStatus
import org.softnetwork.notification.model.{NotificationAck, Notification}

/**
  * Created by smanciot on 14/04/2018.
  */
trait NotificationProvider[T<:Notification] {
  def send(notification: T): NotificationAck
  def ack(uuid: String, currentStatus: NotificationStatus): NotificationAck
}
