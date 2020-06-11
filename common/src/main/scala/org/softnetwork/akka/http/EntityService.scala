package org.softnetwork.akka.http

import akka.actor.typed.ActorSystem
import org.softnetwork.akka.handlers.EntityHandler
import org.softnetwork.akka.message.{CommandResult, Command}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

/**
  * Created by smanciot on 15/04/2020.
  */
trait EntityService[C <: Command, R <: CommandResult] { _: EntityHandler[C, R] =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContext = system.executionContext

  def run(entityId: String, command: C)(implicit atMost: FiniteDuration, tTag: ClassTag[C]): R = {
    this ?? (entityId, command, atMost)
  }

  def runAsync(entityId: String, command: C)(implicit atMost: FiniteDuration, tTag: ClassTag[C]): Future[R] = {
    Future.successful(this ?? (entityId, command, atMost))
  }

}
