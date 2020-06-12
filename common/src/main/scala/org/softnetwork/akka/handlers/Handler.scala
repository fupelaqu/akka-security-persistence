package org.softnetwork.akka.handlers

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import com.typesafe.scalalogging.StrictLogging

import org.softnetwork.akka.message.{CommandWrapper, EntityCommand, CommandResult, Command}
import org.softnetwork.akka.persistence.typed.{EntitySystemLocator, CommandTypeKey}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{reflectiveCalls, implicitConversions}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import EntitySystemLocator._

/**
  * Created by smanciot on 09/04/2020.
  */
//CommandHandler
trait Handler[C <: Command, R <: CommandResult] extends StrictLogging {
  type Request = ActorRef[R] => C

  implicit def command2Request(command: C) : Request

  protected val defaultAtMost = 10.second

  protected def behavior: Behavior[C]

  protected def name: String

  private[this] var ref: Option[ActorRef[C]] = None

  private[this] def actorRef(implicit system: ActorSystem[_]): ActorRef[C] = {
    if(ref.isEmpty){
      ref = Some(system.systemActorOf(behavior, name))
    }
    ref.get
  }

  def ?(command: C, atMost: FiniteDuration = defaultAtMost): R = {
    implicit val timeout: Timeout = atMost
    implicit val aSystem: ActorSystem[_] = system()
    Try(Await.result(actorRef ? command, atMost)) match {
      case Success(r) => r
      case Failure(f) =>
        logger.error(f.getMessage, f)
        throw f
    }
  }

  def !(command: C): Unit = {
    implicit val aSystem: ActorSystem[_] = system()
    actorRef ! command
  }

}

trait EntityHandler[C <: Command, R <: CommandResult] extends StrictLogging {_: CommandTypeKey[C] =>

  final val ALL_KEY = "*"

  type Request = ActorRef[R] => C

  implicit def command2Request(command: C) : Request = replyTo => CommandWrapper(command, replyTo)

  protected val defaultAtMost = 10.second

  private[this] def entityRef(entityId: String)(implicit tTag: ClassTag[C]) = entityRefFor(TypeKey, entityId)

  private[this] def ask(entityId: String, command: C, atMost: FiniteDuration = defaultAtMost)(
    implicit tTag: ClassTag[C]): R = {
    implicit val timeout: Timeout = atMost
    Try(Await.result(entityRef(entityId) ? command, atMost)) match {
      case Success(r) => r
      case Failure(f) =>
        logger.error(f.getMessage, f)
        throw f
    }
  }

  private[this] def tell(entityId: String, command: C)(implicit tTag: ClassTag[C]): Unit = {
    entityRef(entityId) ! command
  }

  def ?(entityId: String, command: C, atMost: FiniteDuration = defaultAtMost)(implicit tTag: ClassTag[C]): R =
    this.ask(entityId, command, atMost)

  def !(entityId: String, command: C)(implicit tTag: ClassTag[C]): Unit =
    this.tell(entityId, command)

  implicit def key2String[T](key: T): String = key match {
    case s: String => s
    case _         => key.toString
  }

  protected def lookup[T](key: T): Option[String] = Some(key)

  def ??[T](key: T, command: C, atMost: FiniteDuration = defaultAtMost)(implicit tTag: ClassTag[C]): R = {
    lookup(key) match {
      case Some(entityId) => this ? (entityId, command, atMost)
      case _              => this ? (key, command, atMost)
    }
  }

  def ?![T](key: T, command: C)(implicit tTag: ClassTag[C]): Unit = {
    lookup(key) match {
      case Some(entityId) => this ! (entityId, command)
      case _              => this ! (key, command)
    }
  }

  def *?[T](keys: List[T], command: C, atMost: FiniteDuration = defaultAtMost)(implicit tTag: ClassTag[C]): List[R] = {
    for(key <- keys) yield lookup(key) match {
      case Some(entityId) => this ? (entityId, command, atMost)
      case _ => this ? (key, command, atMost)
    }
  }

  def *![T](keys: List[T], command: C)(implicit tTag: ClassTag[C]): Unit = {
    for(key <- keys) yield lookup(key) match {
      case Some(entityId) => this ! (entityId, command)
      case _ => this ? (key, command)
    }
  }

  def !?(command: C, atMost: FiniteDuration = defaultAtMost)(implicit tTag: ClassTag[C]): R =
    command match {
      case cmd: EntityCommand => this ? (cmd.id, command, atMost)
      case _ => this ? (ALL_KEY, command, atMost)
    }

  def !!(command: C)(implicit tTag: ClassTag[C]): Unit =
    command match {
      case cmd: EntityCommand => this ! (cmd.id, command)
      case _ => this ! (ALL_KEY, command)
    }

}

