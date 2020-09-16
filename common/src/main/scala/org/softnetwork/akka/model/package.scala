package org.softnetwork.akka

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Date

import java.sql.Timestamp
import com.markatta.akron.CronExpression

import scala.concurrent.duration._

/**
  * Created by smanciot on 27/05/2020.
  */
package object model {

  /** State objects **/
  trait State{
    def uuid: String
  }

  trait Timestamped extends State {
    def lastUpdated: Date
    def createdDate: Date
  }

  trait ProtobufDomainObject

  trait CborDomainObject

  trait SchedulerItem {
    def persistenceId: String
    def entityId: String
    def key: String
    val uuid = s"$persistenceId#$entityId#$key"
  }

  trait CronTabItem {
    def cron: String
    val cronExpression = CronExpression(cron)
    def nextLocalDateTime(): Option[LocalDateTime] = {
      cronExpression.nextTriggerTime(LocalDateTime.now())
    }
    def next(from: Option[Date] = None): Option[FiniteDuration] = {
      (if(from.isDefined) Some(new Timestamp(from.get.getTime).toLocalDateTime) else nextLocalDateTime()) match {
        case Some(ldt) =>
          val diff = LocalDateTime.now().until(ldt, ChronoUnit.SECONDS)
          if(diff < 0){
            Some(Math.max(1, 60 - Math.abs(diff)).seconds)
          }
          else{
            Some(diff.seconds)
          }

        case _ => None
      }
    }
  }
}
