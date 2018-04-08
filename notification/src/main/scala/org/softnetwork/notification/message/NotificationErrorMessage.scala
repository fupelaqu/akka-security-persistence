package org.softnetwork.notification.message

import org.softnetwork.akka.message.ErrorMessage

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationErrorMessage (override val message: String) extends ErrorMessage(message) with NotificationCommandResult

case object MailNotDelivered extends NotificationErrorMessage("MailNotDelivered")

case object NotificationNotDelivered extends NotificationErrorMessage("NotificationNotDelivered")