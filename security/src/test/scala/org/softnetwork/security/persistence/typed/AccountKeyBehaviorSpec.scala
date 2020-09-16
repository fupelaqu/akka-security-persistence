package org.softnetwork.security.persistence.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.akka.message.CommandWrapper

import org.softnetwork.security.message._

/**
  * Created by smanciot on 19/04/2020.
  */
class AccountKeyBehaviorSpec extends PersistenceTypedActorTestKit with AnyWordSpecLike {

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      AccountKeyBehavior.init(context.system)
      Behaviors.empty
    }
  }

  import AccountKeyBehavior._

  "AccountKey" must {
    "add key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "add")
      ref ! CommandWrapper(AddAccountKey("account"), probe.ref)
      probe.expectMessage(AccountKeyAdded("add", "account"))
    }

    "remove key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "remove")
      ref ! AddAccountKey("account")
      ref ! CommandWrapper(RemoveAccountKey, probe.ref)
      probe.expectMessage(AccountKeyRemoved("remove"))
    }

    "lookup key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "lookup")
      ref ! AddAccountKey("account")
      ref ! CommandWrapper(LookupAccountKey, probe.ref)
      probe.expectMessage(AccountKeyFound("account"))
      val ref2 = entityRefFor(TypeKey, "empty")
      ref2 ! CommandWrapper(LookupAccountKey, probe.ref)
      probe.expectMessage(AccountKeyNotFound)
    }
  }
}
