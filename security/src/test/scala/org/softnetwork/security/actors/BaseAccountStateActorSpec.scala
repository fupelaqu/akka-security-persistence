package org.softnetwork.security.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.akka.message.CommandResult
import org.softnetwork.kafka.api.KafkaSpec
import org.softnetwork.notification.actors.MockNotificationActor
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.security.message._
import org.softnetwork.security.model.AccountStatus

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by smanciot on 19/03/2018.
  */
class BaseAccountStateActorSpec extends WordSpec with Matchers with KafkaSpec {

  val zookeeper              = s"localhost:${kafkaServer.zookeeperPort}"

  val broker                 = s"localhost:${kafkaServer.kafkaPort}"

  var actorSystem: ActorSystem = _

  var baseAccountActor: ActorRef = _

  implicit val timeout_      = Timeout(10.seconds)

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
                                            |    """.stripMargin)

  private val username = "smanciot"

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "0660010203"

  private val password = "changeit"

  override def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = ActorSystem.create("testAccount", config)
    val notificationHandler = new NotificationHandler(
      actorSystem.actorOf(
        MockNotificationActor.props(), "notificationActor"
      )
    )
    baseAccountActor = actorSystem.actorOf(BaseAccountStateActor.props(notificationHandler), "baseAccountStateActor")
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  "SignIn" should {
    "fail if confirmed password does not match password" in {
      Await.result((baseAccountActor ? SignIn(username, password, "fake")).mapTo[CommandResult], 10.second) match {
        case _: PasswordsNotMatched.type =>
        case _                           => fail()
      }
    }
    "work with username" in {
      Await.result((baseAccountActor ? SignIn(username, password, password)).mapTo[CommandResult], 1.second) match {
        case r: AccountCreated[_]  => r.account.status shouldBe AccountStatus.Inactive
        case _                     => fail()
      }
    }
    "fail if username already exists" in {
      Await.result((baseAccountActor ? SignIn(username, password, password)).mapTo[CommandResult], 1.second) match {
        case _: LoginAlreadyExists.type  =>
        case _                           => fail()
      }
    }
    "work with email" in {
      Await.result((baseAccountActor ? SignIn(email, password, password)).mapTo[CommandResult], 1.second) match {
        case r: AccountCreated[_]  => r.account.status shouldBe AccountStatus.Inactive
        case _                     => fail()
      }
    }
    "fail if email already exists" in {
      Await.result((baseAccountActor ? SignIn(email, password, password)).mapTo[CommandResult], 1.second) match {
        case _: LoginAlreadyExists.type  =>
        case _                           => fail()
      }
    }
    "work with gsm" in {
      Await.result((baseAccountActor ? SignIn(gsm, password, password)).mapTo[CommandResult], 1.second) match {
        case r: AccountCreated[_]  => r.account.status shouldBe AccountStatus.Inactive
        case _                     => fail()
      }
    }
    "fail if gsm already exists" in {
      Await.result((baseAccountActor ? SignIn(gsm, password, password)).mapTo[CommandResult], 1.second) match {
        case _: LoginAlreadyExists.type  =>
        case _                           => fail()
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      Await.result(baseAccountActor ? SignIn(username, password, password), 1.second)
      Await.result((baseAccountActor ? Login(username, password)).mapTo[CommandResult], 1.second) match {
        case _: LoginSucceeded[_]  =>
        case _                     => fail()
      }
    }
    "work with matching email and password" in {
      Await.result(baseAccountActor ? SignIn(email, password, password), 1.second)
      Await.result((baseAccountActor ? Login(email, password)).mapTo[CommandResult], 1.second) match {
        case _: LoginSucceeded[_]  =>
        case _                     => fail()
      }
    }
    "work with matching gsm and password" in {
      Await.result(baseAccountActor ? SignIn(gsm, password, password), 1.second)
      Await.result((baseAccountActor ? Login(gsm, password)).mapTo[CommandResult], 1.second) match {
        case _: LoginSucceeded[_]  =>
        case _                     => fail()
      }
    }
    "fail with unknown username" in {
      Await.result(baseAccountActor ? SignIn(username, password, password), 1.second)
      Await.result((baseAccountActor ? Login("fake", password)).mapTo[CommandResult], 1.second) match {
        case _: LoginAndPasswordNotMatched.type  =>
        case _                                   => fail()
      }
    }
    "fail with unknown email" in {
      Await.result(baseAccountActor ? SignIn(email, password, password), 1.second)
      Await.result((baseAccountActor ? Login("fake@gmail.com", password)).mapTo[CommandResult], 1.second) match {
        case _: LoginAndPasswordNotMatched.type  =>
        case _                                   => fail()
      }
    }
    "fail with unknown gsm" in {
      Await.result(baseAccountActor ? SignIn(gsm, password, password), 1.second)
      Await.result((baseAccountActor ? Login("0102030405", password)).mapTo[CommandResult], 1.second) match {
        case _: LoginAndPasswordNotMatched.type  =>
        case _                                   => fail()
      }
    }
    "fail with unmatching username and password" in {
      Await.result(baseAccountActor ? SignIn(username, password, password), 1.second)
      Await.result((baseAccountActor ? Login(username, "fake")).mapTo[CommandResult], 1.second) match {
        case _: LoginAndPasswordNotMatched.type  =>
        case _                                   => fail()
      }
    }
    "fail with unmatching email and password" in {
      Await.result(baseAccountActor ? SignIn(email, password, password), 1.second)
      Await.result((baseAccountActor ? Login(email, "fake")).mapTo[CommandResult], 1.second) match {
        case _: LoginAndPasswordNotMatched.type  =>
        case _                                   => fail()
      }
    }
    "fail with unmatching gsm and password" in {
      Await.result(baseAccountActor ? SignIn(gsm, password, password), 1.second)
      Await.result((baseAccountActor ? Login(gsm, "fake")).mapTo[CommandResult], 1.second) match {
        case _: LoginAndPasswordNotMatched.type  =>
        case _                                    => fail()
      }
    }
    "disable profile after n login failures" in {
      Await.result(baseAccountActor ? SignIn(gsm, password, password), 1.second)
      Await.result(baseAccountActor ? Login(gsm, password), 1.second) // reset number of failures
      val failures = (0 to BaseAccountStateActor.maxFailures) // max number of failures + 1
        .map(_ => Await.result((baseAccountActor ? Login(gsm, "fake")).mapTo[CommandResult], 1.second))
      failures.last match {
        case _: AccountDisabled.type =>
        case _                       => fail()
      }
    }
  }

  "SignOut" should {
    "work" in {
      Await.result(baseAccountActor ? SignIn(gsm, password, password), 1.second)
      Await.result((baseAccountActor ? Login(gsm, password)).mapTo[CommandResult], 1.second) match {
        case r: LoginSucceeded[_] =>
          Await.result((baseAccountActor ? SignOut(r.account.uuid)).mapTo[CommandResult], 1.second) match {
            case r: AccountDeleted[_] => r.account.status shouldBe AccountStatus.Deleted
            case _                    => fail()
        }
        case _                    => fail(s"Login failed for $gsm:$password")
      }
    }
  }
}
