package org.softnetwork.security.actors

import akka.actor.ActorRef
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.notification.actors.{PushActor, SMSActor, MailActor}
import org.softnetwork.notification.handlers.{PushHandler, SMSHandler, MailHandler}
import org.softnetwork.security.handlers.DefaultGenerator

/**
  * Created by smanciot on 22/03/2018.
  */
trait ActorsModule {

  lazy val mailHandler: MailHandler = new MailHandler(
    ActorSystemLocator().actorOf(
      MailActor.props(), "mailActor"
    )
  )

  lazy val smsHandler: SMSHandler = new SMSHandler(
    ActorSystemLocator().actorOf(
      SMSActor.props(), "smsActor"
    )
  )

  lazy val pushHandler: PushHandler = new PushHandler(
    ActorSystemLocator().actorOf(
      PushActor.props(), "pushActor"
    )
  )

  lazy val accountStateActor: ActorRef = ActorSystemLocator().actorOf(
    BaseAccountStateActor.props(mailHandler, smsHandler, pushHandler, new DefaultGenerator), "accountStateActor"
  )

}
