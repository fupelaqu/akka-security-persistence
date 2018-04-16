package org.softnetwork.notification.message

import org.softnetwork.akka.message.{Event, CommandResult}
import org.softnetwork.notification.model.Notification

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationCommandResult extends CommandResult

case class NotificationRecordedEvent[T<:Notification](uuid: String, notification: T) extends Event

case class NotificationAdded(uuid: String) extends NotificationCommandResult

case object NotificationRemoved extends NotificationCommandResult

case class NotificationRemovedEvent(uuid: String) extends Event

case class NotificationSent(uuid: String) extends NotificationCommandResult

case class NotificationDelivered(uuid: String) extends NotificationCommandResult
