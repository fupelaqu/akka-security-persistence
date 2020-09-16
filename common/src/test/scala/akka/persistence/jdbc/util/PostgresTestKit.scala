package akka.persistence.jdbc.util

import com.typesafe.config.ConfigFactory
import org.softnetwork.akka.persistence.jdbc.util.Postgres
import org.softnetwork.docker.PostgresService

trait PostgresTestKit extends PostgresService with Postgres {

  lazy val slick = s"""
                      |slick {
                      |  profile = "slick.jdbc.PostgresProfile$$"
                      |  db {
                      |    url = "jdbc:postgresql://$PostgresHost:$PostgresPort/$PostgresDB?reWriteBatchedInserts=true"
                      |    user = "$PostgresUser"
                      |    password = "$PostgresPassword"
                      |    driver = "org.postgresql.Driver"
                      |    numThreads = 5
                      |    maxConnections = 5
                      |    minConnections = 1
                      |    idleTimeout = 10000 //10 seconds
                      |  }
                      |}
                      |""".stripMargin

  override lazy val cfg = ConfigFactory.parseString(slick)

//  override lazy val db = SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")

  override def beforeAll(): Unit = {
    super.beforeAll()
    waitForContainerUp()
    initSchema()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }

}