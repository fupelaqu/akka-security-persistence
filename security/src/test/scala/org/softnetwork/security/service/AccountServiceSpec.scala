package org.softnetwork.security.service

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.kafka.api.KafkaSpec
import org.softnetwork.notification.actors.MockNotificationActor
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.security.actors.BaseAccountStateActor
import org.softnetwork.security.handlers.AccountHandler
import org.softnetwork.security.message._
import org.softnetwork.security.model.AccountStatus
import org.softnetwork.session.actors.SessionRefreshTokenStateActor
import org.softnetwork.session.handlers.SessionRefreshTokenHandler

import scala.concurrent.duration._


/**
  * Created by smanciot on 20/03/2018.
  */
class AccountServiceSpec extends WordSpec with Matchers with KafkaSpec {

  val zookeeper              = s"localhost:${kafkaServer.zookeeperPort}"

  val broker                 = s"localhost:${kafkaServer.kafkaPort}"

  var actorSystem: ActorSystem = _

  var accountService: AccountService = _

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
    actorSystem = ActorSystem.create("testAccountService", config)
    val notificationHandler = new NotificationHandler(
      actorSystem.actorOf(
        MockNotificationActor.props(), "notificationActor"
      )
    )
    accountService = new AccountService(
      new AccountHandler(
        actorSystem.actorOf(BaseAccountStateActor.props(notificationHandler), "baseAccountStateActor")
      ),
      new SessionRefreshTokenHandler(
        actorSystem.actorOf(SessionRefreshTokenStateActor.props(), "sessionRefreshTokenStateActor")
      )
    )(actorSystem.dispatcher)
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  "SignIn" should {
    "fail if confirmed password does not match password" in {
      accountService.run(SignIn(username, password, "fake"), 10.second) match {
        case _: PasswordsNotMatched.type =>
        case _                           => fail()
      }
    }
    "work with username" in {
      accountService.run(SignIn(username, password, password)) match {
        case r: AccountCreated[_] => r.account.status shouldBe AccountStatus.Inactive
        case _                    => fail()
      }
    }
    "fail if username already exists" in {
      accountService.run(SignIn(username, password, password)) match {
        case _: LoginAlreadyExists.type  =>
        case _                           => fail()
      }
    }
    "work with email" in {
      accountService.run(SignIn(email, password, password)) match {
        case r: AccountCreated[_] => r.account.status shouldBe AccountStatus.Inactive
        case _                    => fail()
      }
    }
    "fail if email already exists" in {
      accountService.run(SignIn(email, password, password)) match {
        case _: LoginAlreadyExists.type  =>
        case _                           => fail()
      }
    }
    "work with gsm" in {
      accountService.run(SignIn(gsm, password, password)) match {
        case r: AccountCreated[_] => r.account.status shouldBe AccountStatus.Inactive
        case _                    => fail()
      }
    }
    "fail if gsm already exists" in {
      accountService.run(SignIn(gsm, password, password)) match {
        case _: LoginAlreadyExists.type  =>
        case _                           => fail()
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      accountService.run(SignIn(username, password, password))
      accountService.run(Login(username, password)) match {
        case _: LoginSucceeded[_] =>
        case _                    => fail()
      }
    }
    "work with matching email and password" in {
      accountService.run(SignIn(email, password, password))
      accountService.run(Login(email, password)) match {
        case _: LoginSucceeded[_] =>
        case _                    => fail()
      }
    }
    "work with matching gsm and password" in {
      accountService.run(SignIn(gsm, password, password))
      accountService.run(Login(gsm, password)) match {
        case _: LoginSucceeded[_] =>
        case _                    => fail()
      }
    }
    "fail with unknown username" in {
      accountService.run(SignIn(username, password, password))
      accountService.run(Login("fake", password)) match {
        case _: LoginAndPasswordNotMatched.type =>
        case _                                  => fail()
      }
    }
    "fail with unknown email" in {
      accountService.run(SignIn(email, password, password))
      accountService.run(Login("fake@gmail.com", password)) match {
        case _: LoginAndPasswordNotMatched.type =>
        case _                                  => fail()
      }
    }
    "fail with unknown gsm" in {
      accountService.run(SignIn(gsm, password, password))
      accountService.run(Login("0102030405", password)) match {
        case _: LoginAndPasswordNotMatched.type =>
        case _                                  => fail()
      }
    }
    "fail with unmatching username and password" in {
      accountService.run(SignIn(username, password, password))
      accountService.run(Login(username, "fake")) match {
        case _: LoginAndPasswordNotMatched.type =>
        case _                                  => fail()
      }
    }
    "fail with unmatching email and password" in {
      accountService.run(SignIn(email, password, password))
      accountService.run(Login(email, "fake")) match {
        case _: LoginAndPasswordNotMatched.type =>
        case _                                  => fail()
      }
    }
    "fail with unmatching gsm and password" in {
      accountService.run(SignIn(gsm, password, password))
      accountService.run(Login(gsm, "fake")) match {
        case _: LoginAndPasswordNotMatched.type =>
        case _                                  => fail()
      }
    }
    "disable account after n login failures" in {
      accountService.run(SignIn(gsm, password, password))
      accountService.run(Login(gsm, password)) // reset number of failures
      val failures = (0 to BaseAccountStateActor.maxFailures) // max number of failures + 1
          .map(_ => accountService.run(Login(gsm, "fake")))
      failures.last match {
        case _: AccountDisabled.type =>
        case _                       => fail()
      }
    }
  }

  "SignOut" should {
    "work" in {
      accountService.run(SignIn(gsm, password, password))
      accountService.run(Login(gsm, password)) match {
        case r: LoginSucceeded[_] =>
          accountService.run(SignOut(r.account.uuid)) match {
            case r: AccountDeleted[_] => r.account.status shouldBe AccountStatus.Deleted
            case _                    => fail()
          }
        case _                    => fail(s"Login failed for $gsm:$password")
      }
    }
  }
}
