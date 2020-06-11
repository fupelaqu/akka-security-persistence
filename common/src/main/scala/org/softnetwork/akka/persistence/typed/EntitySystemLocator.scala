package org.softnetwork.akka.persistence.typed

import akka.actor.typed._

import akka.cluster.sharding.typed.scaladsl.{EntityRef, EntityTypeKey, ClusterSharding}

import com.typesafe.scalalogging.StrictLogging

import org.softnetwork.akka.message.Command

import scala.language.implicitConversions

import scala.util.{Failure, Success, Try}

import akka.{actor => classic}

/**
  * Created by smanciot on 09/04/2020.
  */
object EntitySystemLocator extends StrictLogging {

  private[this] var _system: Option[ActorSystem[_]] = None

  def apply(system: ActorSystem[_]): Unit = {
    _system = Some(system)
  }

  def entityRefFor[R <: Command](typeKey: EntityTypeKey[R], entityId: String): EntityRef[R] =
    Try(ClusterSharding(system()).entityRefFor(typeKey, entityId)) match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(
          s"""
             |Could not find entity for ${typeKey.name}|$entityId
             |using ${_system.map(_.path.toString).getOrElse("UNKNOWN")}
             |""".stripMargin)
        throw f
    }

  def system(): ActorSystem[_] = _system match {
    case Some(s) => s
    case _       => throw new IllegalStateException
  }

  def classicActorSystem(): classic.ActorSystem = system()

}
