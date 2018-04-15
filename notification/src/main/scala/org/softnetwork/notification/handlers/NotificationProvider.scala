package org.softnetwork.notification.handlers

import org.softnetwork.notification.model.{NotificationAck, Notification}

/**
  * Created by smanciot on 14/04/2018.
  */
trait NotificationProvider[T<:Notification] {
  def send(notification: T): NotificationAck
}
