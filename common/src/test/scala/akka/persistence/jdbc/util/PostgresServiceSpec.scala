package akka.persistence.jdbc.util

import org.scalatest.FlatSpec

class PostgresServiceSpec extends FlatSpec with PostgresTestKit {

  "postgres node" should "be ready with log line checker" in {
    waitForContainerUp()
  }
}