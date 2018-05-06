package org.softnetwork.notification.message

import org.softnetwork.akka.message.ErrorMessage

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationErrorMessage (override val message: String) extends ErrorMessage(message) with NotificationCommandResult

case class NotificationUndelivered(uuid: String) extends NotificationErrorMessage("NotificationNotDelivered")

case class NotificationRejected(uuid: String) extends NotificationErrorMessage("NotificationRejected")

case object NotificationNotFound extends NotificationErrorMessage("NotificationNotFound")

case object NotificationMaxTriesReached extends NotificationErrorMessage("NotificationMaxTriesReached")

case object NotificationUnknownCommand extends NotificationErrorMessage("NotificationUnknownCommand")
