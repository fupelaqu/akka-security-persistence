package org.softnetwork.akka.handlers

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.jdbc.util.PersistenceTypedActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.akka.persistence.typed._

import org.softnetwork.akka.message._

import SequenceMessages._

/**
  * Created by smanciot on 19/03/2020.
  */
class SequenceHandlerSpec extends SequenceHandler with PersistenceTypedActorTestKit with AnyWordSpecLike {

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      Sequence.init(context.system)
      Behaviors.empty
    }
  }

  implicit lazy val system = typedSystem()

  "Sequence" must {
    "handle Inc" in {
      this !! IncSequence("inc")
      this !? LoadSequence("inc") match {
        case s: SequenceLoaded => s.value shouldBe 1
        case _                       => fail()
      }
    }
    "handle Dec" in {
      this !! IncSequence("dec")
      this !! IncSequence("dec")
      this !! DecSequence("dec")
      this !? LoadSequence("dec") match {
        case s: SequenceLoaded => s.value shouldBe 1
        case _                       => fail()
      }
    }
    "handle Reset" in {
      this !! IncSequence("reset")
      this !! IncSequence("reset")
      this !! ResetSequence("reset")
      this !? LoadSequence("reset") match {
        case s: SequenceLoaded => s.value shouldBe 0
        case _                       => fail()
      }
    }
    "handle Load" in {
      this !! IncSequence("load")
      this !? LoadSequence("load") match {
        case s: SequenceLoaded => s.value shouldBe 1
        case _                       => fail()
      }
    }
  }
}
