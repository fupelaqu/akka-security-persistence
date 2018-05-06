package org.softnetwork.notification.handlers

import akka.actor.ActorRef
import org.softnetwork.akka.http.Handler
import org.softnetwork.notification.message.{NotificationCommandResult, NotificationCommand}

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationHandler(val notificationSupervisorActor: ActorRef)
  extends Handler[NotificationCommand, NotificationCommandResult]{
  override val actor = notificationSupervisorActor
}
