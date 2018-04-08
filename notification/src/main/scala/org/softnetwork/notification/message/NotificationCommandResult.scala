package org.softnetwork.notification.message

import org.softnetwork.akka.message.CommandResult

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationCommandResult extends CommandResult

case object MailSent extends NotificationCommandResult
