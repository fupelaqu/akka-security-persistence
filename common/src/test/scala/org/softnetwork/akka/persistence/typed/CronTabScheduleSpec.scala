package org.softnetwork.akka.persistence.typed

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
  * Created by smanciot on 03/05/2020.
  */
class CronTabScheduleSpec extends AnyWordSpecLike with Matchers {

  "Cron tab" must {
    "schedule timer every minute" in {
      val cronTabCommand = CronTabCommand("cron1", "* * * * *")
      val next = cronTabCommand.next().get.toSeconds
      (next <= 60) shouldBe true
    }
  }
}
