package akka.http.scaladsl.testkit

import akka.actor.ActorSystem
import akka.persistence.jdbc.util.PersistenceTypedActorTestKit
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{BeforeAndAfterAll, Suite}

/**
  * Created by smanciot on 24/04/2020.
  *
  */
trait PersistenceTypedRouteTestKit extends PersistenceTypedActorTestKit
  with RouteTest
  with Scalatest
  with ScalatestUtils { this: Suite =>

  override def system: ActorSystem

  override protected def createActorSystem(): ActorSystem = {
    import org.softnetwork.akka.persistence.typed._
    typedSystem()
  }

}

/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 *
 */
trait Scalatest extends TestFrameworkInterface with BeforeAndAfterAll {
  this: Suite =>

  def failTest(msg: String) = throw new TestFailedException(msg, 11)

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    cleanUp()
    super.afterAll()
  }

}
