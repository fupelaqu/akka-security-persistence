package org.softnetwork.session.actors

import akka.actor.Props
import org.softnetwork.session.Session

/**
  * Created by smanciot on 22/03/2018.
  */
class SessionRefreshTokenStateActor extends RefreshTokenStateActor[Session]{
  /** number of events received before generating a snapshot - should be configurable **/
  override def snapshotInterval: Long = SessionRefreshTokenStateActor.snapshotInterval

  override def persistenceId: String = "session-refresh-token-state"
}

object SessionRefreshTokenStateActor{
  def props() = Props(new SessionRefreshTokenStateActor())
  val snapshotInterval = 1000
}