package org.softnetwork.akka.http

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.akka.message.{Command, CommandResult}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

/**
  * Created by smanciot on 19/03/2018.
  */
trait Handler[T <: Command, R <: CommandResult] extends StrictLogging {

  def actor: ActorRef

  implicit val timeout                      = Timeout(10.seconds)

  val defaultAtMost = 1.second

  def handle(command: T, atMost: Duration = defaultAtMost)(implicit c: ClassTag[R]): R = {
    Await.result((actor ? command).mapTo[R], atMost)
  }

  def handleAsync(command: T)(implicit c: ClassTag[R]): Future[R] = (actor ? command).mapTo[R]
}
