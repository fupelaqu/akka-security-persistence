package org.softnetwork.akka.http

import org.softnetwork.akka.message.{Command, CommandResult}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
  * Created by smanciot on 20/03/2018.
  */
trait Service[U <: Command, V <: CommandResult] {

  def handler: Handler[U, V]

  val defaultAtMost = 1.second

  def run(command: U, atMost: Duration = defaultAtMost)(implicit c: ClassTag[V]): V = {
    handler.handle(command, atMost)
  }

  def runAsync(command: U)(implicit c: ClassTag[V]): Future[V] = {
    handler.handleAsync(command)
  }

}
