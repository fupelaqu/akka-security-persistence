package org.softnetwork.akka.message

import org.softnetwork.akka.model.{SchedulerItem, CronTab, Schedule}

/**
  * Created by smanciot on 04/09/2020.
  */
object SchedulerMessages {

  sealed trait SchedulerCommand extends Command

  case object ResetCronTabs extends SchedulerCommand

  case object ResetScheduler extends SchedulerCommand

  case class AddSchedule(schedule: Schedule) extends SchedulerCommand

  case class TriggerSchedule(persistenceId: String, entityId: String, key: String) extends SchedulerCommand
    with SchedulerItem

  case class RemoveSchedule(persistenceId: String, entityId: String, key: String) extends SchedulerCommand
    with SchedulerItem

  case class AddCronTab(cronTab: CronTab) extends SchedulerCommand

  case class TriggerCronTab(persistenceId: String, entityId: String, key: String) extends SchedulerCommand
    with SchedulerItem

  case class RemoveCronTab(persistenceId: String, entityId: String, key: String) extends SchedulerCommand with
    SchedulerItem

  sealed trait SchedulerCommandResult extends CommandResult

  case object SchedulerReseted extends SchedulerCommandResult

  case object ScheduleAdded extends SchedulerCommandResult

  case object ScheduleTriggered extends SchedulerCommandResult

  case object ScheduleRemoved extends SchedulerCommandResult

  case object CronTabAdded extends SchedulerCommandResult

  case object CronTabTriggered extends SchedulerCommandResult

  case object CronTabRemoved extends SchedulerCommandResult

  class SchedulerErrorMessage (override val message: String) extends ErrorMessage(message) with SchedulerCommandResult

  case object SchedulerNotFound extends SchedulerErrorMessage("SchedulerNotFound")

  case object ScheduleNotFound extends SchedulerErrorMessage("ScheduleNotFound")

  case object ScheduleNotAdded extends SchedulerErrorMessage("ScheduleNotAdded")

  case object CronTabNotFound extends SchedulerErrorMessage("CronTabNotFound")

  case object CronTabNotAdded extends SchedulerErrorMessage("CronTabNotAdded")
}
