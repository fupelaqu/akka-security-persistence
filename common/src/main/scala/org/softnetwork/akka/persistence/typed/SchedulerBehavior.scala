package org.softnetwork.akka.persistence.typed

import java.sql.Timestamp

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{TimerScheduler, ActorContext}
import akka.persistence.typed.scaladsl.Effect

import org.softnetwork._

import org.softnetwork.akka.message.SchedulerEvents._
import org.softnetwork.akka.message.SchedulerMessages._
import org.softnetwork.akka.model.Scheduler

import scala.concurrent.duration._

/**
  * Created by smanciot on 04/09/2020.
  */
trait SchedulerBehavior extends EntityBehavior[SchedulerCommand, Scheduler, SchedulerEvent, SchedulerCommandResult] {

  private case object ResetCronTabsTimerKey

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event    - the event to tag
    * @return event tags
    */
  override protected def tagEvent(entityId: String, event: SchedulerEvent): Set[String] =
    event match {
      case e: ScheduleTriggeredEvent => Set(s"${e.schedule.persistenceId}-scheduler")
      case e: CronTabTriggeredEvent => Set(s"${e.cronTab.persistenceId}-scheduler")
      case _ => super.tagEvent(entityId, event)
    }

  /**
    *
    * @param entityId - entity identity
    * @param state    - current state
    * @param command  - command to handle
    * @param replyTo  - optional actor to reply to
    * @param timers   - scheduled messages associated with this entity behavior
    * @return effect
    */
  override def handleCommand(entityId: String, state: Option[Scheduler], command: SchedulerCommand,
                             replyTo: Option[ActorRef[SchedulerCommandResult]], timers: TimerScheduler[SchedulerCommand]
                            )(implicit context: ActorContext[SchedulerCommand]
  ): Effect[SchedulerEvent, Option[Scheduler]] =
    command match {
      case ResetCronTabs =>
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.foreach{cronTab =>
              context.self ! AddCronTab(cronTab)
            }
          case _ =>
        }
        context.log.info("Cron tabs reseted")
        Effect.none
      case ResetScheduler => // add all schedules
        state match {
          case Some(scheduler) =>
            Effect.none.thenRun(_ => {
              scheduler.schedules.foreach{schedule =>
                context.self ! AddSchedule(schedule)
              }
              scheduler.cronTabs.foreach{cronTab =>
                context.self ! AddCronTab(cronTab)
              }
              timers.startTimerWithFixedDelay(ResetCronTabsTimerKey, ResetCronTabs, 60.seconds)
              context.log.info("Scheduler reseted")
            })
          case _ => Effect.none
        }
      case cmd: AddSchedule => // add a new schedule which will be triggered repeatedly or once after the delay specified in seconds
        import cmd._
        Effect.persist(ScheduleAddedEvent(schedule)).thenRun(
          state => {
            if(schedule.repeatedly.getOrElse(false)){
              timers.startTimerWithFixedDelay(
                schedule.uuid,
                TriggerSchedule(schedule.persistenceId, schedule.entityId, schedule.key),
                schedule.delay.seconds
              )
            }
            else{
              timers.startSingleTimer(
                schedule.uuid,
                TriggerSchedule(schedule.persistenceId, schedule.entityId, schedule.key),
                schedule.delay.seconds
              )
            }
            context.log.info(s"Schedule ${schedule.uuid} added")
            ScheduleAdded ~> replyTo
          }
        )
      case cmd: TriggerSchedule => // effectively trigger the schedule and remove it if defined to be triggered only once
        state match {
          case Some(scheduler) =>
            scheduler.schedules.find(schedule =>
              schedule.uuid == cmd.uuid
            ) match {
              case Some(schedule) =>
                Effect.persist(ScheduleTriggeredEvent(schedule)).thenRun(_ => {
                  if(!schedule.repeatedly.getOrElse(false))
                    context.self ! RemoveSchedule(schedule.persistenceId, schedule.entityId, schedule.key)
                  context.log.info(s"Schedule ${schedule.uuid} triggered")
                  ScheduleTriggered ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => ScheduleNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case cmd: RemoveSchedule =>
        state match {
          case Some(scheduler) =>
            scheduler.schedules.find(schedule =>
              schedule.uuid == cmd.uuid
            ) match {
              case Some(schedule) =>
                Effect.persist(
                  List(
                    ScheduleRemovedEvent(cmd.persistenceId, cmd.entityId, cmd.key)
                  )
                ).thenRun(_ => {
                  timers.cancel(schedule.uuid)
                  context.log.info(s"Schedule ${schedule.uuid} removed")
                  ScheduleRemoved ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => ScheduleNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case cmd: AddCronTab => // add a new cron tab which will be triggered once after the delay specified in seconds
        (state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.cronTab.uuid
            ) match {
              case Some(cronTab) if cronTab.cron == cmd.cronTab.cron =>
                if(cronTab.nextTriggered.isEmpty){ // next trigger undefined
                  cronTab.nextLocalDateTime() match {
                    case Some(ldt) => Some(cronTab.withNextTriggered(Timestamp.valueOf(ldt)))
                    case _ => Some(cronTab)
                  }
                }
                else if(cronTab.nextTriggered.get.before(now())){ // next trigger defined in the past
                  cronTab.lastTriggered match {
                    case Some(last) if last.after(cronTab.nextTriggered.get) => // cron tab not missed
                      cronTab.nextLocalDateTime() match {
                        case Some(ldt) => Some(cronTab.withNextTriggered(Timestamp.valueOf(ldt)))
                        case _ => Some(cronTab)
                      }
                    case _ => // cron tab missed
                      Some(cronTab)
                  }
                }
                else { // next trigger defined in the future
                  Some(cronTab)
                }
              case _ => // new cron tab
                cmd.cronTab.nextLocalDateTime() match {
                  case Some(ldt) => Some(cmd.cronTab.withNextTriggered(Timestamp.valueOf(ldt)))
                  case _ => Some(cmd.cronTab)
                }
            }
          case _ => None
        }) match {
          case Some(cronTab) =>
            Effect.persist(CronTabAddedEvent(cronTab)).thenRun(
              state => {
                cronTab.next(cronTab.nextTriggered) match {
                  case Some(duration) =>
                    timers.startSingleTimer(
                      cronTab.uuid,
                      TriggerCronTab(cronTab.persistenceId, cronTab.entityId, cronTab.key),
                      duration
                    )
                  case _ =>
                }
                context.log.info(s"CronTab ${cronTab.uuid} added")
                CronTabAdded ~> replyTo
              }
            )
          case _ => Effect.none.thenRun(_ => CronTabNotAdded ~> replyTo)
        }
      case cmd: TriggerCronTab => // trigger the cron tab
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.uuid
            ) match {
              case Some(cronTab) =>
                Effect.persist(CronTabTriggeredEvent(cronTab)).thenRun(_ => {
/* and schedule its next execution as long as the cron expression is satisfied
                  cronTab.next() match {
                    case Some(duration) =>
                      timers.startSingleTimer(
                        cronTab.uuid,
                        TriggerCronTab(cronTab.persistenceId, cronTab.entityId, cronTab.key),
                        duration
                      )
                    case _ =>
                  }
*/
                  context.log.info(s"CronTab ${cronTab.uuid} triggered at ${cronTab.nextTriggered}")
                  CronTabTriggered ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => CronTabNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case cmd: RemoveCronTab =>
        state match {
          case Some(scheduler) =>
            scheduler.cronTabs.find(cronTab =>
              cronTab.uuid == cmd.uuid
            ) match {
              case Some(cronTab) =>
                Effect.persist(
                  List(
                    CronTabRemovedEvent(cmd.persistenceId, cmd.entityId, cmd.key)
                  )
                ).thenRun(_ => {
                  timers.cancel(cronTab.uuid)
                  context.log.info(s"CronTab ${cronTab.uuid} removed")
                  CronTabRemoved ~> replyTo
                })
              case _ => Effect.none.thenRun(_ => CronTabNotFound ~> replyTo)
            }
          case _ => Effect.none.thenRun(_ => SchedulerNotFound ~> replyTo)
        }
      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[Scheduler], event: SchedulerEvent)(
    implicit context: ActorContext[SchedulerCommand]): Option[Scheduler] =
    event match {
      case evt: ScheduleAddedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                schedules =
                  s.schedules.filterNot(schedule =>
                    schedule.uuid == evt.schedule.uuid
                  ) :+ evt.schedule
              )
            case _ => Scheduler("*", schedules = Seq(evt.schedule))
          }
        )
      case evt: ScheduleRemovedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                schedules = s.schedules.filterNot(schedule =>
                  schedule.uuid == evt.uuid
                )
              )
            case _ => Scheduler("*")
          }
        )
      case evt: CronTabAddedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                cronTabs =
                  s.cronTabs.filterNot(cronTab =>
                    cronTab.uuid == evt.cronTab.uuid
                  ) :+ evt.cronTab
              )
            case _ => Scheduler("*", cronTabs = Seq(evt.cronTab))
          }
        )
      case evt: CronTabRemovedEvent =>
        Some(
          state match {
            case Some(s) =>
              s.copy(
                cronTabs = s.cronTabs.filterNot(cronTab =>
                  cronTab.uuid == evt.uuid
                )
              )
            case _ => Scheduler("*")
          }
        )
      case _ => super.handleEvent(state, event)
    }
}

object SchedulerBehavior extends SchedulerBehavior {
  override val persistenceId: String = "Scheduler"
}
