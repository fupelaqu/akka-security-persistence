package org.softnetwork.security.service

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Cookie, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.akka.http.HealthCheckService
import org.softnetwork.akka.http.Implicits._
import org.softnetwork.kafka.api.KafkaSpec
import org.softnetwork.notification.actors.MockNotificationSupervisor
import org.softnetwork.notification.handlers.NotificationHandler
import org.softnetwork.security.actors.BaseAccountStateActor
import org.softnetwork.security.config.Settings
import org.softnetwork.security.handlers.{AccountHandler, MockGenerator}
import org.softnetwork.security.message._
import org.softnetwork.security.model.{BaseAccountInfo, AccountStatus}
import org.softnetwork.session.actors.SessionRefreshTokenStateActor
import org.softnetwork.session.handlers.SessionRefreshTokenHandler

import scala.concurrent.duration._

/**
  * Created by smanciot on 22/03/2018.
  */
class MainRoutesSpec extends WordSpec with Matchers with ScalatestRouteTest with KafkaSpec with Json4sSupport {

  var actorSystem: ActorSystem = _

  implicit val timeout         = Timeout(10.seconds)

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
                                            |    """.stripMargin)

  var mainRoutes: MainRoutes = _

  var accountHandler: AccountHandler = _

  private val username = "smanciot"

  private val firstName = Some("Stephane")

  private val lastName = Some("Manciot")

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "0660010203"

  private val password = "changeit"

  override def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = ActorSystem.create("testMainRoutes", config)

    val notificationHandler = new NotificationHandler(
      actorSystem.actorOf(
        MockNotificationSupervisor.props(),
        "notifications"
      )
    )

    accountHandler = new AccountHandler(
      actorSystem.actorOf(
        BaseAccountStateActor.props(
          notificationHandler,
          new MockGenerator
        ),
        "baseAccountStateActor"
      )
    )
    mainRoutes = new MainRoutes(
      new HealthCheckService(),
      new AccountService(
        accountHandler,
        new SessionRefreshTokenHandler(
          actorSystem.actorOf(SessionRefreshTokenStateActor.props(), "sessionRefreshTokenStateActor")
        )
      )(actorSystem.dispatcher)
    )
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  "MainRoutes" should {
    "contain a healthcheck path" in {
      Get("/api/healthcheck") ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "SignIn" should {
    "fail if confirmed password does not match password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(username, password, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe PasswordsNotMatched.message
      }
    }
    "work with username" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(username, password, password, firstName, lastName))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[BaseAccountInfo].status shouldBe AccountStatus.Inactive
      }
    }
    "fail if username already exists" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(username, password, password))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe LoginAlreadyExists.message
      }
    }
    "work with email" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password, firstName, lastName))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[BaseAccountInfo].status shouldBe AccountStatus.Inactive
      }
    }
    "fail if email already exists" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
    "work with gsm" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password, firstName, lastName))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[BaseAccountInfo].status shouldBe AccountStatus.Inactive
      }
    }
    "fail if gsm already exists" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(username, password, password, firstName, lastName)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(username, password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Accepted
        responseAs[BaseAccountInfo].status shouldBe AccountStatus.Inactive
      }
    }
    "work with matching email and password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password, firstName, lastName)) ~> mainRoutes.routes
      Get(s"/api/${Settings.Path}/activate", Activate("token")) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(email, password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
    "work with matching gsm and password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password, firstName, lastName)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(gsm, password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
    "fail with unknown username" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(username, password, password)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login("fake", password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown email" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login("fake@gmail.com", password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown gsm" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login("0102030405", password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching username and password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(username, password, password)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(username, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching email and password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password)) ~> mainRoutes.routes
      Get(s"/api/${Settings.Path}/activate", Activate("token")) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(email, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching gsm and password" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(gsm, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "disable account after n login failures" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password, firstName, lastName)) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/login", Login(gsm, password)) ~> mainRoutes.routes  // reset number of failures
      val failures = (0 to BaseAccountStateActor.maxFailures) // max number of failures + 1
          .map(_ => Post(s"/api/${Settings.Path}/login", Login(gsm, "fake")) ~> mainRoutes.routes )
      failures.last ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual AccountDisabled.message
      }
    }
  }

  "Logout" should {
    "work" in {
      var _headers: Seq[HttpHeader] = Seq.empty
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password, firstName, lastName)) ~> mainRoutes.routes
      accountHandler.handle(UpdatePassword(gsm, password, password, password)) // FIXME account may have been previously disabled
      Post(s"/api/${Settings.Path}/login", Login(gsm, password, refreshable = true)) ~> mainRoutes.routes ~> check {  // reset number of failures
        status shouldEqual StatusCodes.Accepted
        _headers = headers
      }
      Post(s"/api/${Settings.Path}/logout").withHeaders(extractCookies(_headers):_*) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "SignOut" should {
    "work" in {
      var _headers: Seq[HttpHeader] = Seq.empty
      Post(s"/api/${Settings.Path}/signIn", SignIn(gsm, password, password, firstName, lastName)) ~> mainRoutes.routes
      accountHandler.handle(UpdatePassword(gsm, password, password, password)) // FIXME account may have been previously disabled
      Post(s"/api/${Settings.Path}/login", Login(gsm, password, refreshable = true)) ~> mainRoutes.routes ~> check {  // reset number of failures
        status shouldEqual StatusCodes.Accepted
        _headers = headers
      }
      Post(s"/api/${Settings.Path}/signOut").withHeaders(extractCookies(_headers):_*) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
//FIXME        responseAs[Profile].status shouldEqual AccountStatus.Deleted
      }
    }
  }

  "SendVerificationCode" should {
    "work" in {
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password)) ~> mainRoutes.routes
      Get(s"/api/${Settings.Path}/activate", Activate("token")) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/sendVerificationCode", SendVerificationCode(email)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      var _headers: Seq[HttpHeader] = Seq.empty
      Post(s"/api/${Settings.Path}/signIn", SignIn(email, password, password)) ~> mainRoutes.routes
      Get(s"/api/${Settings.Path}/activate", Activate("token")) ~> mainRoutes.routes
      Post(s"/api/${Settings.Path}/sendVerificationCode", SendVerificationCode(email)) ~> mainRoutes.routes ~> check {  // reset number of failures
        status shouldEqual StatusCodes.OK
        _headers = headers
      }
      Post(s"/api/${Settings.Path}/resetPassword", ResetPassword("code", password, password))
        .withHeaders(extractCookies(_headers):_*)  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  def extractCookies(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    headers.filter((header) => {
      val name = header.lowercaseName()
      println(s"$name:${header.value}")
      name == "set-cookie"
    }).flatMap((header) => {
      val cookie = header.value().split("=")
      val name = cookie.head
      val value = cookie.tail.mkString("").split(";").head
      var ret: Seq[HttpHeader] = Seq(Cookie(name, value))
      if(name == "XSRF-TOKEN")
        ret = ret ++ Seq(RawHeader("X-XSRF-TOKEN", value))
      ret
    })
  }
}
