package org.softnetwork.akka.http

import akka.actor.typed.ActorSystem
import org.softnetwork.akka.handlers.Handler
import org.softnetwork.akka.message.{Command, CommandResult}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * Created by smanciot on 15/04/2020.
  */
trait Service[C <: Command, R <: CommandResult] { _: Handler[C, R] =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec: ExecutionContext = system.executionContext

  def run(command: C)(implicit atMost: FiniteDuration, tTag: ClassTag[C]): R = {
    this ? (command, atMost)
  }

  def runAsync(command: C)(implicit atMost: FiniteDuration, tTag: ClassTag[C]): Future[R] = {
    Future.successful(this ? (command, atMost))
  }

}
