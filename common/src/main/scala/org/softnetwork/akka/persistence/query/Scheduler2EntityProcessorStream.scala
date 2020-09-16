package org.softnetwork.akka.persistence.query

import akka.Done
import akka.persistence.typed.PersistenceId
import org.softnetwork.akka.handlers.EntityHandler
import org.softnetwork.akka.message.{CommandResult, Command}
import org.softnetwork.akka.message.SchedulerEvents.{CronTabTriggeredEvent, ScheduleTriggeredEvent, SchedulerEvent}
import org.softnetwork.akka.model.{CronTab, Schedule}

import scala.concurrent.Future

/**
  * Created by smanciot on 04/09/2020.
  */
trait Scheduler2EntityProcessorStream[C <: Command, R <: CommandResult] extends EventProcessorStream[SchedulerEvent] {
  _: JournalProvider with EntityHandler[C, R] =>
  /**
    *
    * Processing event
    *
    * @param event         - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr    - sequence number
    * @return
    */
  override protected final def processEvent(
                                             event: SchedulerEvent,
                                             persistenceId: PersistenceId,
                                             sequenceNr: Long): Future[Done] = {
    event match {
      case evt: ScheduleTriggeredEvent =>
        if(triggerSchedule(evt.schedule)) {
          Future.successful(Done)
        }
        else{
          Future.failed(
            new Exception(
              s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
            )
          )
        }
      case evt: CronTabTriggeredEvent =>
        if(triggerCronTab(evt.cronTab)) {
          Future.successful(Done)
        }
        else{
          Future.failed(
            new Exception(
              s"event ${persistenceId.id} for sequence $sequenceNr could not be processed by $platformEventProcessorId"
            )
          )
        }
      case _ => Future.successful(Done)
    }
  }

  /**
    *
    * @param schedule - the schedule to trigger
    * @return true if the schedule has been successfully triggered, false otherwise
    */
  protected def triggerSchedule(schedule: Schedule): Boolean = false

  /**
    *
    * @param cronTab - the cron tab to trigger
    * @return true if the cron tab has been successfully triggered, false otherwise
    */
  protected def triggerCronTab(cronTab: CronTab): Boolean = false
}
