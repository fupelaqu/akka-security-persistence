package org.softnetwork.notification.handlers

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.notification.actors.MockNotificationActor
import org.softnetwork.notification.message.{MailSent, SendMail}
import org.softnetwork.notification.model.Mail

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationHandlerSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  var actorSystem: ActorSystem = _

  var notificationHandler: NotificationHandler = _

  val config = ConfigFactory.parseString(s"""
                                            |    akka {
                                            |      logger-startup-timeout = 10s
                                            |    }
                                            |
                                            |    """.stripMargin)

  val from = ("pierre.durand@gmail.com", "Pierre Durand")
  val to = Seq("nobody@gmail.com")
  val subject = "Sujet"
  val message = "message"

  override def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = ActorSystem.create("testNotificationHandler", config)
    ActorSystemLocator(actorSystem)
    notificationHandler = new NotificationHandler(
      actorSystem.actorOf(MockNotificationActor.props(), "notificationActor")
    )
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  "SendMail" should {
    "be sent" in {
      notificationHandler.handle(SendMail(Mail(from = from, to = to, subject = subject, message = message))) match {
        case _: MailSent.type =>
        case _                => fail
      }
    }
  }

}
