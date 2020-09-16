package org.softnetwork.akka.persistence

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.TimerScheduler
import org.softnetwork.akka.message.Command
import org.softnetwork.akka.model.CronTabItem

import scala.concurrent.duration._

import scala.language.implicitConversions

/**
  * Created by smanciot on 16/05/2020.
  */
package object typed {

  import akka.{actor => classic}

  implicit def typed2classic(system: ActorSystem[_]): classic.ActorSystem = {
    import akka.actor.typed.scaladsl.adapter._
    system.toClassic
  }

  /**
    * To schedule a message to be sent repeatedly to the self actor as long as the cron expression is satisfied (
    * as long as next() will return Some)
    * key - a unique identifier for the underlying schedule
    * cronExpression - a simplified subset of the time expression part of the V7 cron expression standard
    */
  abstract class CronTabCommand(val key: Any, val cron: String) extends Command with CronTabItem

  /**
    * CronTabCommand companion object
    */
  object CronTabCommand {
    def apply(key: Any, cron: String): CronTabCommand = {
      new CronTabCommand(key, cron){}
    }
  }

  /**
    * To schedule a message to be sent either once after the given `delay` or repeatedly with a
    * fixed delay between messages to the self actor
    *
    * @tparam C - the type of Command to schedule
    */
  case class ScheduleCommand[C <: Command](key: Any, command: C, maybeDelay: Option[FiniteDuration] = None, once: Boolean = false) {
    def timer : TimerScheduler[C] => Unit = timers => {
      maybeDelay match {
        case Some(delay) =>
          if(once){
            timers.startSingleTimer(key, command, delay)
          }
          else{
            timers.startTimerWithFixedDelay(key, command, delay)
          }
        case _ =>
      }
    }
  }

  /**
    * Schedule companion object
    */
  object ScheduleCommand {

    implicit def cronTabCommandToSchedule[C <: Command](cronTabCommand: CronTabCommand): ScheduleCommand[C] = {
      ScheduleCommand(
        cronTabCommand.key,
        cronTabCommand.asInstanceOf[C],
        cronTabCommand.next(),
        once = true
      )
    }
  }

}
