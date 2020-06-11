package org.softnetwork.security.handlers

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike

import org.softnetwork.security.persistence.typed._

/**
  * Created by smanciot on 19/04/2020.
  */
class AccountKeyDaoSpec extends AccountKeyDao with AccountKeyHandler with AnyWordSpecLike with PersistenceTypedActorTestKit {

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      AccountKeyBehavior.init(context.system)
      Behaviors.empty
    }
  }

  implicit lazy val system = typedSystem()

  "AccountKey" must {
    "add key" in {
      addAccountKey("add", "account")
      lookupAccount("add") match {
        case Some(account) => account shouldBe "account"
        case _             => fail()
      }
    }

    "remove key" in {
      addAccountKey("remove", "account")
      removeAccountKey("remove")
      lookupAccount("remove") match {
        case Some(account) => fail()
        case _             =>
      }
    }
  }

}
