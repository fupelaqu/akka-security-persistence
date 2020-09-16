package org.softnetwork.akka.handlers

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.softnetwork.akka.message.SchedulerMessages._
import org.softnetwork.akka.model.{CronTab, Schedule}
import org.softnetwork.akka.persistence.typed.{SchedulerBehavior, CommandTypeKey}

import scala.reflect.ClassTag

/**
  * Created by smanciot on 04/09/2020.
  */
trait SchedulerTypeKey extends CommandTypeKey[SchedulerCommand]{
  override def TypeKey(implicit tTag: ClassTag[SchedulerCommand]): EntityTypeKey[SchedulerCommand] =
    SchedulerBehavior.TypeKey
}

trait SchedulerHandler extends EntityHandler[SchedulerCommand, SchedulerCommandResult] with SchedulerTypeKey

object SchedulerHandler extends SchedulerHandler

trait SchedulerDao { _: SchedulerHandler =>

  def resetScheduler(): Boolean =
    !? (ResetScheduler) match {
      case SchedulerReseted => true
      case _ => false
    }

  def addSchedule(schedule: Schedule): Boolean =
    !? (AddSchedule(schedule)) match {
      case ScheduleAdded => true
      case _ => false
    }

  def removeSchedule(persistenceId: String, entityId: String, key: String): Boolean =
    !? (RemoveSchedule(persistenceId, entityId, key)) match {
      case ScheduleRemoved => true
      case _ => false
    }

  def addCronTab(cronTab: CronTab): Boolean =
    !? (AddCronTab(cronTab)) match {
      case CronTabAdded => true
      case _ => false
    }

  def removeCronTab(persistenceId: String, entityId: String, key: String): Boolean =
    !? (RemoveCronTab(persistenceId, entityId, key)) match {
      case CronTabRemoved => true
      case _ => false
    }

}

object SchedulerDao extends SchedulerDao with SchedulerHandler
