package org.softnetwork.notification.message

import org.softnetwork.akka.message.Command
import org.softnetwork.notification.model.Notification

/**
  * Created by smanciot on 07/04/2018.
  */
sealed trait NotificationCommand extends Command

case class AddNotification[T<:Notification](notification: T) extends NotificationCommand

case class RemoveNotification(uuid: String) extends NotificationCommand

case class SendNotification[T<:Notification](notification: T) extends NotificationCommand

case class ResendNotification(uuid: String) extends NotificationCommand

case class GetNotificationStatus(uuid: String) extends NotificationCommand

trait MailCommand

trait SMSCommand

trait PushCommand
