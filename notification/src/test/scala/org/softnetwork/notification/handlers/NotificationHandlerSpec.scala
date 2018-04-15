package org.softnetwork.notification.handlers

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.kafka.api.KafkaSpec
import org.softnetwork.notification.actors.MockNotificationActor
import org.softnetwork.notification.message._
import org.softnetwork.notification.model.Mail

import scala.concurrent.duration._

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationHandlerSpec extends WordSpec with Matchers with KafkaSpec {

  val zookeeper                  = s"localhost:${kafkaServer.zookeeperPort}"

  val broker                     = s"localhost:${kafkaServer.kafkaPort}"

  var actorSystem: ActorSystem = _

  implicit val timeout           = Timeout(10.seconds)

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
      actorSystem.actorOf(MockNotificationActor.props(), "notificationActor")
    )
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  "SendMail" should {
    "be sent" in {
      notificationHandler.handle(
        SendNotification(
          Mail(
            from = from,
            to = to,
            subject = subject,
            message = message
          )
        )
      ) match {
        case _: NotificationSent =>
        case _                   => fail
      }
    }
  }

}
