package org.softnetwork.session.actors

import akka.actor.Props
import org.softnetwork.build.info.session.BuildInfo
import org.softnetwork.session.Session

/**
  * Created by smanciot on 22/03/2018.
  */
class SessionRefreshTokenStateActor extends RefreshTokenStateActor[Session]{
  /** number of events received before generating a snapshot - should be configurable **/
  override def snapshotInterval: Long = SessionRefreshTokenStateActor.snapshotInterval

  override def persistenceId: String = s"session-refresh-token-state-${BuildInfo.gitCurrentBranch.replace("/", "_")}"
}

object SessionRefreshTokenStateActor{
  def props() = Props(new SessionRefreshTokenStateActor())
  val snapshotInterval = 1000
}