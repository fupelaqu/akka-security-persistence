package org.softnetwork.akka.persistence

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.TimerScheduler
import com.markatta.akron.CronExpression
import org.softnetwork.akka.message.Command

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
  abstract class CronTabCommand(val key: Any, val cronExpression: CronExpression) extends Command {
    def next(): Option[FiniteDuration] = {
      val now = LocalDateTime.now()
      cronExpression.nextTriggerTime(now) match {
        case Some(ldt) =>
          val diff = now.until(ldt, ChronoUnit.SECONDS)
          if(diff < 0){
            Some((60 - Math.abs(diff)).seconds)
          }
          else{
            Some(diff.seconds)
          }

        case _ => None
      }
    }
  }

  /**
    * CronTabCommand companion object
    */
  object CronTabCommand {
    def apply(key: Any, cron: String): CronTabCommand = {
      new CronTabCommand(key, CronExpression(cron)){}
    }
  }

  /**
    * To schedule a message to be sent either once after the given `delay` or repeatedly with a
    * fixed delay between messages to the self actor
    *
    * @tparam C - the type of Command to schedule
    */
  case class Schedule[C <: Command](key: Any, command: C, maybeDelay: Option[FiniteDuration] = None, once: Boolean = false) {
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
  object Schedule {

    implicit def cronTabCommandToSchedule[C <: Command](cronTabCommand: CronTabCommand): Schedule[C] = {
      Schedule(
        cronTabCommand.key,
        cronTabCommand.asInstanceOf[C],
        cronTabCommand.next(),
        once = true
      )
    }
  }

}
