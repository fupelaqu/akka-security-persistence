package org.softnetwork.security.handlers

import com.softwaremill.macwire._
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.security.actors.ActorsModule
import org.softnetwork.session.actors.SessionRefreshTokenStateActor
import org.softnetwork.session.handlers.SessionRefreshTokenHandler

/**
  * Created by smanciot on 20/03/2018.
  */
trait HandlersModule extends ActorsModule {

  lazy val accountHandler: AccountHandler = wire[AccountHandler]

  lazy val sessionRefreshTokenHandler: SessionRefreshTokenHandler =
    new SessionRefreshTokenHandler(
      ActorSystemLocator().actorOf(
        SessionRefreshTokenStateActor.props(), "refreshTokenStateActor"
      )
    )

}
