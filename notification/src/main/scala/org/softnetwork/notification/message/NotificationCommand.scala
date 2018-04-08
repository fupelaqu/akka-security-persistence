package org.softnetwork.notification.message

import org.softnetwork.akka.message.Command
import org.softnetwork.notification.model.Mail

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationCommand extends Command

case class SendMail(mail: Mail) extends NotificationCommand
