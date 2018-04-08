package org.softnetwork.session.handlers

import akka.actor.ActorRef
import org.softnetwork.session.Session

/**
  * Created by smanciot on 22/03/2018.
  */
class SessionRefreshTokenHandler(refreshTokenStateActor: ActorRef) extends RefreshTokenHandler[Session](refreshTokenStateActor)
