package org.softnetwork.notification.handlers

import akka.actor.ActorRef
import org.softnetwork.akka.http.Handler
import org.softnetwork.notification.message.{NotificationCommandResult, NotificationCommand}
import org.softnetwork.notification.model.{Push, SMS, Mail, Notification}

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationHandler[T <: Notification] extends Handler[NotificationCommand, NotificationCommandResult]

class MailHandler(val mailActor: ActorRef) extends NotificationHandler[Mail]{
  override val actor: ActorRef = mailActor
}

class SMSHandler(val smsActor: ActorRef) extends NotificationHandler[SMS]{
  override val actor: ActorRef = smsActor
}

class PushHandler(val pushActor: ActorRef) extends NotificationHandler[Push]{
  override val actor: ActorRef = pushActor
}