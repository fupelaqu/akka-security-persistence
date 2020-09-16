package org.softnetwork.notification.peristence.query

import akka.actor.typed.eventstream.EventStream.Publish
import org.softnetwork.akka.model.Schedule
import org.softnetwork.akka.persistence.query.{JournalProvider, Scheduler2EntityProcessorStream}
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.notification.message.{Schedule4NotificationTriggered, TriggerSchedule4Notification, NotificationCommandResult, NotificationCommand}

/**
  * Created by smanciot on 04/09/2020.
  */
trait Scheduler2NotificationProcessorStream
  extends Scheduler2EntityProcessorStream[NotificationCommand, NotificationCommandResult] {
  _: JournalProvider with NotificationHandler =>

  protected val forTests = false

  override protected def triggerSchedule(schedule: Schedule): Boolean = {
    !? (TriggerSchedule4Notification(schedule)) match {
      case Schedule4NotificationTriggered =>
        if(forTests){
          system.eventStream.tell(Publish(Schedule4NotificationTriggered))
        }
        true
      case other => false
    }
  }
}
