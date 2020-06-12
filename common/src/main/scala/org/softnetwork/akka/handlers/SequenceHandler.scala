package org.softnetwork.akka.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.softnetwork.akka.persistence.typed._

import org.softnetwork.akka.message._

import SequenceMessages._

import scala.reflect.ClassTag

import scala.language.implicitConversions

/**
  * Created by smanciot on 09/04/2020.
  */
trait SequenceTypeKey extends CommandTypeKey[SequenceCommand]{
  override def TypeKey(implicit tTag: ClassTag[SequenceCommand]): EntityTypeKey[SequenceCommand] = Sequence.TypeKey
}

trait SequenceHandler extends EntityHandler[SequenceCommand, SequenceResult] with SequenceTypeKey {

  implicit def command2Request(command: SequenceCommand) : Request =
    replyTo => SequenceCommandWrapper(command, replyTo)

}

object SequenceHandler extends SequenceHandler

trait SequenceDao{_: SequenceHandler =>

  def inc(sequence: String)(implicit system: ActorSystem[_]) = {
    !? (IncSequence(sequence)) match {
      case r: SequenceIncremented => Left(r.value)
      case other => Right(other)
    }
  }

  def dec(sequence: String)(implicit system: ActorSystem[_]) = {
    !? (DecSequence(sequence)) match {
      case r: SequenceDecremented => Left(r.value)
      case other => Right(other)
    }
  }

  def reset(sequence: String)(implicit system: ActorSystem[_]) = {
    !? (ResetSequence(sequence)) match {
      case r: SequenceResetted => Left(r)
      case other => Right(other)
    }
  }

  def load(sequence: String)(implicit system: ActorSystem[_]) = {
    !? (LoadSequence(sequence)) match {
      case r: SequenceLoaded => Left(r.value)
      case other => Right(other)
    }
  }

}

object SequenceDao extends SequenceDao with SequenceHandler