package org.softnetwork.notification

import akka.actor.typed.ActorRef
import org.softnetwork.akka.message._
import org.softnetwork.notification.model.Notification

/**
  * Created by smanciot on 15/04/2020.
  */
package object message {

  sealed trait NotificationCommand extends EntityCommand

  @SerialVersionUID(0L)
  case class NotificationTimeout() extends NotificationCommand with AllEntities

  @SerialVersionUID(0L)
  case class NotificationCommandWrapper[C <: NotificationCommand, R <: NotificationCommandResult]
  (command: C, replyTo: ActorRef[R]) extends CommandWrapper[C, R] with NotificationCommand{
    override val id = command.id
  }

  @SerialVersionUID(0L)
  case class AddNotification[T<:Notification](notification: T) extends NotificationCommand {
    override val id = notification.uuid
  }

  @SerialVersionUID(0L)
  case class RemoveNotification(id: String) extends NotificationCommand

  @SerialVersionUID(0L)
  case class SendNotification[T<:Notification](notification: T) extends NotificationCommand {
    override val id = notification.uuid
  }

  @SerialVersionUID(0L)
  case class ResendNotification(id: String) extends NotificationCommand

  @SerialVersionUID(0L)
  case class GetNotificationStatus(id: String) extends NotificationCommand

  sealed trait NotificationCommandResult extends CommandResult

  @SerialVersionUID(0L)
  case class NotificationAdded(uuid: String) extends NotificationCommandResult

  case object NotificationRemoved extends NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationSent(uuid: String) extends NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationDelivered(uuid: String) extends NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationPending(uuid: String) extends NotificationCommandResult

  @SerialVersionUID(0L)
  class NotificationErrorMessage (override val message: String) extends ErrorMessage(message) with NotificationCommandResult

  @SerialVersionUID(0L)
  case class NotificationUndelivered(uuid: String) extends NotificationErrorMessage("NotificationNotDelivered")

  @SerialVersionUID(0L)
  case class NotificationRejected(uuid: String) extends NotificationErrorMessage("NotificationRejected")

  case object NotificationNotFound extends NotificationErrorMessage("NotificationNotFound")

  case object NotificationMaxTriesReached extends NotificationErrorMessage("NotificationMaxTriesReached")

  case object NotificationUnknownCommand extends NotificationErrorMessage("NotificationUnknownCommand")
}
