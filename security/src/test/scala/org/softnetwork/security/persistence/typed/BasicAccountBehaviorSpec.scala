package org.softnetwork.security.persistence.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.jdbc.util.PersistenceTypedActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.security.message._

import MockBasicAccountBehavior._

/**
  * Created by smanciot on 19/04/2020.
  */
class BasicAccountBehaviorSpec extends PersistenceTypedActorTestKit with AnyWordSpecLike {

  private val username = "smanciot"

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "33660010203"

  private val gsm2 = "33660020304"

  private val password = "Changeit1"

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      MockBasicAccountBehavior.init(context.system)
      Behaviors.empty
    }
  }

  "SignUp" must {
    "fail if confirmed password does not match password" in {
      val probe = createTestProbe[AccountCommandResult]()
      val ref = entityRefFor(TypeKey, "PasswordsNotMatched")
      ref ! AccountCommandWrapper(SignUp(username, password, Some("fake")), probe.ref)
      probe.expectMessage(PasswordsNotMatched)
    }
  }
}
