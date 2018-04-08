package org.softnetwork.security.handlers

import akka.actor.ActorRef
import org.softnetwork.akka.http.Handler
import org.softnetwork.security.message.{AccountCommandResult, AccountCommand}

/**
  * Created by smanciot on 01/04/2018.
  */
class AccountHandler(val accountStateActor: ActorRef) extends Handler[AccountCommand, AccountCommandResult]{

  override def actor: ActorRef = accountStateActor

}
