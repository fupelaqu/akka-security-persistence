package org.softnetwork.security.persistence.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike

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
      ref ! AccountKeyCommandWrapper(AddAccountKey("account"), probe.ref)
      probe.expectMessage(AccountKeyAdded("add", "account"))
    }

    "remove key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "remove")
      ref ! AddAccountKey("account")
      ref ! AccountKeyCommandWrapper(RemoveAccountKey, probe.ref)
      probe.expectMessage(AccountKeyRemoved("account"))
    }

    "lookup key" in {
      val probe = createTestProbe[AccountKeyCommandResult]()
      val ref = entityRefFor(TypeKey, "lookup")
      ref ! AddAccountKey("account")
      ref ! AccountKeyCommandWrapper(LookupAccountKey, probe.ref)
      probe.expectMessage(AccountKeyFound("account"))
      val ref2 = entityRefFor(TypeKey, "empty")
      ref2 ! AccountKeyCommandWrapper(LookupAccountKey, probe.ref)
      probe.expectMessage(AccountKeyNotFound)
    }
  }
}
