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
    if(_system.isEmpty){
      _system = Some(system)
      system.registerOnTermination(() => _system = None)
    }
  }

  def entityRefFor[C <: Command](typeKey: EntityTypeKey[C], entityId: String): EntityRef[C] =
    Try(ClusterSharding(system()).entityRefFor(typeKey, entityId)) match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(
          s"""
             |Could not find entity for ${typeKey.name}|$entityId
             |using ${_system.map(_.path.toString).getOrElse("Unknown system")}
             |""".stripMargin)
        throw f
    }

  def system(): ActorSystem[_] = _system match {
    case Some(s) => s
    case _       => throw new IllegalStateException(
      "a call to init(ActorSystem[_]) should be made prior to use EntitySystemLocator"
    )
  }

  def classicSystem(): classic.ActorSystem = system()

}
