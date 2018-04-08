package org.softnetwork.notification.actors

import akka.actor.{Props, Actor, ActorLogging}
import org.softnetwork.notification.handlers.{MockMailHandler, MailHandler}
import org.softnetwork.notification.message.{MailNotDelivered, NotificationErrorMessage, MailSent, SendMail}

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationActor extends Actor with ActorLogging { self: MailHandler =>

  import org.softnetwork.notification.config.Settings._

  implicit val mailConfig: MailConfig = config.get

  override def receive: Receive = {
    case cmd: SendMail => sender() ! {if(send(cmd.mail)) MailSent else MailNotDelivered}

    /** no handlers **/
    case _             => sender() ! new NotificationErrorMessage("UnknownCommand")
  }

}

object NotificationActor {
  def props(): Props = Props(new NotificationActor with MailHandler)
}

object MockNotificationActor {
  def props(): Props = Props(new NotificationActor with MockMailHandler)
}
