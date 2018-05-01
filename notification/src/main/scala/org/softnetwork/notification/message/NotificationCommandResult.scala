package org.softnetwork.notification.message

import org.softnetwork.akka.message.{RecordEvent, Event, CommandResult}
import org.softnetwork.notification.model.Notification

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationCommandResult extends CommandResult

class NotificationRecordedEvent[T<:Notification](uuid: String, notification: T)
  extends RecordEvent[String, T](uuid, notification)

case class NotificationAdded(uuid: String) extends NotificationCommandResult

case object NotificationRemoved extends NotificationCommandResult

class NotificationRemovedEvent[T<:Notification](uuid: String) extends RecordEvent[String, T](uuid, null.asInstanceOf[T])

case class NotificationSent(uuid: String) extends NotificationCommandResult

case class NotificationDelivered(uuid: String) extends NotificationCommandResult
