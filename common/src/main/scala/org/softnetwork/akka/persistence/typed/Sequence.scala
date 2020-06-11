package org.softnetwork.akka.persistence.typed

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.{ActorSystem, ActorRef}

import akka.persistence.typed.scaladsl.Effect

import org.slf4j.Logger

import scala.language.implicitConversions

import org.softnetwork.akka.model.SequenceState

import org.softnetwork.akka.message._

import SequenceMessages._

/**
  * Created by smanciot on 18/03/2020.
  */
trait Sequence extends EntityBehavior[SequenceCommand, SequenceState, SequenceEvent, SequenceResult]{
  val persistenceId = "Sequence"

  override def handleCommand( entityId: String,
                              state: Option[SequenceState],
                              command: SequenceCommand,
                              replyTo: Option[ActorRef[SequenceResult]],
                              self: ActorRef[SequenceCommand])(
    implicit system: ActorSystem[_], log: Logger, m: Manifest[SequenceState], timers: TimerScheduler[SequenceCommand]
  ): Effect[SequenceEvent, Option[SequenceState]] = {
    command match {
      case _: IncSequence   => Effect.persist(SequenceIncremented(entityId, state.map(_.value+1).getOrElse(1)))
        .thenRun(maybeReply(replyTo, state => SequenceIncremented(entityId, state.map(_.value).getOrElse(0))))
      case _: DecSequence   => Effect.persist(SequenceDecremented(entityId, state.map(_.value-1).getOrElse(0)))
        .thenRun(maybeReply(replyTo, state => SequenceDecremented(entityId, state.map(_.value).getOrElse(0))))
      case _: ResetSequence => Effect.persist(SequenceResetted(entityId))
        .thenRun(maybeReply(replyTo, state => SequenceResetted(entityId)))
      case _: LoadSequence  => Effect.none
        .thenRun(maybeReply(replyTo, state => SequenceLoaded(entityId, state.map(_.value).getOrElse(0))))
      case _                => Effect.unhandled
    }
  }

  override def handleEvent(state: Option[SequenceState], event: SequenceEvent)
                          (implicit system: ActorSystem[_], log: Logger, m: Manifest[SequenceState]): Option[SequenceState] = {
    event match {
      case e: SequenceIncremented => Some(SequenceState(e.name, e.value))
      case e: SequenceDecremented => Some(SequenceState(e.name, e.value))
      case _: SequenceResetted    => emptyState
      case _                      => state
    }
  }
}

object Sequence extends Sequence
