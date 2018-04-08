package org.softnetwork.session.actors

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.softwaremill.session.{RefreshTokenData, RefreshTokenLookupResult}
import org.softnetwork.akka.message.Event
import org.softnetwork.session.message._

/**
  * Created by smanciot on 22/03/2018.
  */
trait RefreshTokenStateActor[T] extends PersistentActor with ActorLogging {

  /** number of events received before generating a snapshot - should be configurable **/
  def snapshotInterval: Long

  var state = RefreshTokenState[T]()

  def updateState(event: Event): Unit = {
    event match {
      case e: RefreshTokenStored[T] =>
        state = state.copy(tokens = state.tokens.updated(e.data.selector, e.data))
      case e: RefreshTokenRemoved   =>
        state = state.copy(tokens = state.tokens - e.selector)
      case _ =>
    }
  }

  override def receiveRecover: Receive = {
    case e: Event                                         => updateState(e)
    case SnapshotOffer(_, snapshot: RefreshTokenState[T]) => state = snapshot
    case RecoveryCompleted                                => log.info(s"RefreshTokenState has been recovered")
  }

  override def receiveCommand: Receive = {
    case cmd: StoreRefreshToken[T] =>
      persist(RefreshTokenStored[T](cmd.data.copy())) {event =>
        updateState(event)
        context.system.eventStream.publish(event)
        sender() ! event
        performSnapshotIfRequired()
      }
    case cmd: RemoveRefreshToken =>
      persist(RefreshTokenRemoved(cmd.selector)) {event =>
        updateState(event)
        context.system.eventStream.publish(event)
        sender() ! event
        performSnapshotIfRequired()
      }
    case cmd: LookupRefreshToken => sender() ! LookupRefreshTokenResult[T](lookupRefreshToken(cmd.selector))
  }

  private def lookupRefreshToken(selector: String): Option[RefreshTokenLookupResult[T]] =
    state.tokens.get(selector) match {
      case Some(data) => Some(RefreshTokenLookupResult[T](data.tokenHash, data.expires, () => data.forSession))
      case _          => None
    }

  private def performSnapshotIfRequired(): Unit = {
    if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(state)
  }
}

case class RefreshTokenState[T](tokens: Map[String, RefreshTokenData[T]] = Map[String, RefreshTokenData[T]]())