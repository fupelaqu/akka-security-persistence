package org.softnetwork.session.persistence.typed

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.{ActorSystem, ActorRef}

import akka.persistence.typed.scaladsl.Effect

import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData}

import org.slf4j.Logger

import org.softnetwork.akka.model.State

import org.softnetwork.akka.persistence.typed.EntityBehavior
import org.softnetwork.session.Session

import org.softnetwork.session.message._

import scala.language.implicitConversions
import scala.language.postfixOps

/**
  * Created by smanciot on 14/04/2020.
  */
object RefreshToken {
  @SerialVersionUID(0L)
  case class RefreshTokenState[T](data: RefreshTokenData[T]) extends State {
    val uuid = data.selector
  }
}

import RefreshToken._

trait RefreshTokenBehavior[
  T,
  C <: RefreshTokenCommand,
  E <: RefreshTokenEvent,
  R <: RefreshTokenResult]
  extends EntityBehavior[C, RefreshTokenState[T], E, R] {

  implicit def toR[U <: RefreshTokenResult](result: U): R

  implicit def toE[U <: RefreshTokenEvent](event: U): E

  /**
    *
    * @param entityId - entity identity
    * @param state   - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @return effect
    */
  override def handleCommand( entityId: String,
                              state: Option[RefreshTokenState[T]],
                              command: C,
                              replyTo: Option[ActorRef[R]],
                              self: ActorRef[C])(
    implicit system: ActorSystem[_], log: Logger, m: Manifest[RefreshTokenState[T]], timers: TimerScheduler[C]
  ): Effect[E, Option[RefreshTokenState[T]]] = {
    command match {

      case cmd: StoreRefreshToken[T] =>
        Effect.persist[E, Option[RefreshTokenState[T]]](RefreshTokenStored(cmd.data))
          .thenRun(maybeReply(replyTo, _ => RefreshTokenStored(cmd.data)))

      case cmd: RemoveRefreshToken   =>
        Effect.persist[E, Option[RefreshTokenState[T]]](RefreshTokenRemoved(cmd.selector))
          .thenRun(maybeReply(replyTo, _ => RefreshTokenRemoved(cmd.selector))).thenStop()

      case cmd: LookupRefreshToken   =>
        Effect.none.thenRun(
          maybeReply(
            replyTo,
            _ => LookupRefreshTokenResult(
              state.map((s) => RefreshTokenLookupResult(
                s.data.tokenHash,
                s.data.expires,
                () => s.data.forSession
              ))
            )
          )
        )

      case _ => super.handleCommand(entityId, state, command, replyTo, self)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(
                            state: Option[RefreshTokenState[T]],
                            event: E
                          )(implicit system: ActorSystem[_],
                            log: Logger,
                            m: Manifest[RefreshTokenState[T]]
  ): Option[RefreshTokenState[T]] = {
    event match {
      case e: RefreshTokenStored[T] => Some(RefreshTokenState(e.data))
      case _: RefreshTokenRemoved   => emptyState
      case _                        => super.handleEvent(state, event)
    }
  }

}

trait SessionRefreshTokenBehavior extends RefreshTokenBehavior[
  Session,
  RefreshTokenCommand,
  RefreshTokenEvent,
  RefreshTokenResult]{
  override implicit def toR[U <: RefreshTokenResult](result: U): RefreshTokenResult = result

  override implicit def toE[U <: RefreshTokenEvent](event: U): RefreshTokenEvent = event

  override val persistenceId = "Session"

}

object SessionRefreshTokenBehavior extends SessionRefreshTokenBehavior