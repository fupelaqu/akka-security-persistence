package org.softnetwork.notification.handlers

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.kafka.api.KafkaSpec
import org.softnetwork.notification.actors.MockNotificationSupervisor
import org.softnetwork.notification.message._
import org.softnetwork.notification.model.Mail

import scala.concurrent.duration._

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationHandlerSpec extends WordSpec with Matchers with KafkaSpec {

  var actorSystem: ActorSystem         = _

  implicit val timeout                 = Timeout(10.seconds)

  var notificationHandler: NotificationHandler = _

  val config = ConfigFactory.parseString(s"""
                                            |    akka {
                                            |      logger-startup-timeout = 10s
                                            |      persistence {
                                            |        journal {
                                            |          plugin = "kafka-journal"
                                            |        }
                                            |        snapshot-store {
                                            |          plugin = "kafka-snapshot-store"
                                            |        }
                                            |      }
                                            |    }
                                            |
                                            |    # Don't terminate ActorSystem in tests
                                            |    akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
                                            |    akka.coordinated-shutdown.terminate-actor-system = off
                                            |    akka.cluster.run-coordinated-shutdown-when-down = off
                                            |
                                            |    kafka-journal {
                                            |      zookeeper {
                                            |          connect = "$zookeeper"
                                            |      }
                                            |      consumer {
                                            |        bootstrap.servers = "$broker"
                                            |      }
                                            |
                                            |      producer {
                                            |        bootstrap.servers = "$broker"
                                            |      }
                                            |
                                            |      event {
                                            |        producer {
                                            |          bootstrap.servers = "$broker"
                                            |          topic.mapper.class = "akka.persistence.kafka.EmptyEventTopicMapper"
                                            |        }
                                            |      }
                                            |    }
                                            |
                                            |    kafka-snapshot-store {
                                            |      prefix = "snapshot-"
                                            |      consumer {
                                            |        bootstrap.servers = "$broker"
                                            |      }
                                            |      producer {
                                            |        bootstrap.servers = "$broker"
                                            |      }
                                            |    }
                                            |
                                            |    kafka {
                                            |      topic-config.replication = 0
                                            |      topic-config.partitions = 1
                                            |      uri = s"$broker"
                                            |      zookeeper = "$zookeeper"
                                            |    }
                                            |
                                            |    """.stripMargin)

  val from = ("pierre.durand@gmail.com", Some("Pierre Durand"))
  val to = Seq("nobody@gmail.com")
  val subject = "Sujet"
  val message = "message"

  override def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = ActorSystem.create("testNotificationHandler", config)
    ActorSystemLocator(actorSystem)
    notificationHandler = new NotificationHandler(
      actorSystem.actorOf(
        MockNotificationSupervisor.props(),
        "notifications"
      )
    )
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  "NotificationHandler" should {
    "add notification" in {
      notificationHandler.handle(
        new AddNotification(
          Mail(
            from = from,
            to = to,
            subject = subject,
            message = message
          )
        ) with MailCommand
      ) match {
        case _: NotificationAdded =>
        case _                    => fail
      }
    }
    "remove notification" in {
      (notificationHandler.handle(
        new AddNotification(
          Mail(
            from = from,
            to = to,
            subject = subject,
            message = message
          )
        ) with MailCommand
      ) match {
        case n: NotificationAdded => Some(n.uuid)
        case _                    => None
      }) match {
        case Some(uuid) =>
          notificationHandler.handle(new RemoveNotification(uuid) with MailCommand) match {
            case _: NotificationRemoved.type =>
            case _                           => fail
          }
        case _          => fail
      }

    }
    "send notification" in {
      notificationHandler.handle(
        new SendNotification(
          Mail(
            from = from,
            to = to,
            subject = subject,
            message = message
          )
        ) with MailCommand
      ) match {
        case _: NotificationSent =>
        case _                   => fail
      }
    }
    "resend notification" in {
      (notificationHandler.handle(
        new SendNotification(
          Mail(
            from = from,
            to = to,
            subject = subject,
            message = message
          )
        ) with MailCommand
      ) match {
        case n: NotificationSent => Some(n.uuid)
        case _                   => None
      }) match {
        case Some(uuid) =>
          notificationHandler.handle(new ResendNotification(uuid) with MailCommand) match {
            case _: NotificationSent =>
            case _                   => fail
          }
        case _          => fail
      }
    }
    "retrieve notification status" in {
      (notificationHandler.handle(
        new SendNotification(
          Mail(
            from = from,
            to = to,
            subject = subject,
            message = message
          )
        ) with MailCommand
      ) match {
        case n: NotificationSent => Some(n.uuid)
        case _                   => None
      }) match {
        case Some(uuid) =>
          notificationHandler.handle(new GetNotificationStatus(uuid) with MailCommand) match {
            case _: NotificationSent =>
            case _                   => fail
          }
        case _          => fail
      }
    }
  }

}
