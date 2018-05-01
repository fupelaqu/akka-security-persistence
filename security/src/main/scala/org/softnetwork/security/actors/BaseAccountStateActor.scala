package org.softnetwork.security.actors

import akka.actor.Props
import org.softnetwork.notification.handlers.{PushHandler, SMSHandler, MailHandler}
import org.softnetwork.security.handlers.Generator
import org.softnetwork.security.message.SignIn
import org.softnetwork.security.model.BaseAccount

/**
  * Created by smanciot on 17/03/2018.
  */
class BaseAccountStateActor(
  override val mailHandler: MailHandler,
  override val smsHandler: SMSHandler,
  override val pushHandler: PushHandler,
  override val generator: Generator
) extends AccountStateActor[BaseAccount] {

  override val persistenceId: String = "account-state"

  override var state: AccountState[BaseAccount] = AccountState[BaseAccount]()

  /** number of events received before generating a snapshot - should be configurable **/
  override val snapshotInterval = BaseAccountStateActor.snapshotInterval

  /** number of login failures authorized before disabling user account - should be configurable **/
  override val maxFailures = BaseAccountStateActor.maxFailures

  override def createAccount(cmd: SignIn): Option[BaseAccount] = BaseAccount(cmd)
}

object BaseAccountStateActor {
  def props(mailHandler: MailHandler, smsHandler: SMSHandler, pushHandler: PushHandler, generator: Generator) = Props(
    new BaseAccountStateActor(mailHandler, smsHandler, pushHandler, generator)
  )
  val snapshotInterval = 1000L
  val maxFailures = 5
}
