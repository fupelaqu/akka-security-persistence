package org.softnetwork.security.actors

import akka.actor.Props
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.security.handlers.{MockGenerator, DefaultGenerator}
import org.softnetwork.security.message.SignIn
import org.softnetwork.security.model.BaseAccount

/**
  * Created by smanciot on 17/03/2018.
  */
class BaseAccountStateActor(
  override val notificationHandler: NotificationHandler
) extends AccountStateActor[BaseAccount] with DefaultGenerator {

  override val persistenceId: String = "account-state"

  override var state: AccountState[BaseAccount] = AccountState[BaseAccount]()

  override def createAccount(cmd: SignIn): Option[BaseAccount] = BaseAccount(cmd)
}

object BaseAccountStateActor {
  def props(notificationHandler: NotificationHandler) = Props(
    new BaseAccountStateActor(notificationHandler)
  )
}

object MockBaseAccountStateActor {
  def props(notificationHandler: NotificationHandler) = Props(
    new BaseAccountStateActor(notificationHandler) with MockGenerator
  )
}
