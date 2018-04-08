package org.softnetwork.security.actors

import akka.actor.ActorRef
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.notification.actors.NotificationActor
import org.softnetwork.notification.handlers.NotificationHandler

/**
  * Created by smanciot on 22/03/2018.
  */
trait ActorsModule {

  lazy val notificationHandler: NotificationHandler = new NotificationHandler(
    ActorSystemLocator().actorOf(
      NotificationActor.props(), "notificationActor"
    )
  )

  lazy val accountStateActor: ActorRef = ActorSystemLocator().actorOf(
    BaseAccountStateActor.props(notificationHandler), "accountStateActor"
  )

}
