package org.softnetwork.akka.persistence.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike

import Sequence._

import org.softnetwork.akka.message._

import SequenceMessages._

/**
  * Created by smanciot on 19/03/2020.
  */
class SequenceSpec extends PersistenceTypedActorTestKit with AnyWordSpecLike {

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      Sequence.init(context.system)
      Behaviors.empty
    }
  }

  "Sequence" must {
    "handle Inc" in {
      val probe = createTestProbe[SequenceResult]()
      val ref = entityRefFor(TypeKey, "inc")
      ref ! IncSequence("")
      ref ! CommandWrapper(LoadSequence(""), probe.ref)
      probe.expectMessage(SequenceLoaded("inc", 1))
    }
    "handle Dec" in {
      val probe = createTestProbe[SequenceResult]()
      val ref = entityRefFor(TypeKey, "dec")
      ref ! IncSequence("")
      ref ! IncSequence("")
      ref ! DecSequence("")
      ref ! CommandWrapper(LoadSequence(""), probe.ref)
      probe.expectMessage(SequenceLoaded("dec", 1))
    }
    "handle Reset" in {
      val probe = createTestProbe[SequenceResult]()
      val ref = entityRefFor(TypeKey, "reset")
      ref ! IncSequence("")
      ref ! IncSequence("")
      ref ! ResetSequence("")
      ref ! CommandWrapper(LoadSequence(""), probe.ref)
      probe.expectMessage(SequenceLoaded("reset", 0))
    }
    "handle Load" in {
      val probe = createTestProbe[SequenceResult]()
      val ref = entityRefFor(TypeKey, "load")
      ref ! IncSequence("")
      ref ! CommandWrapper(LoadSequence(""), probe.ref)
      probe.expectMessage(SequenceLoaded("load", 1))
    }
  }
}
