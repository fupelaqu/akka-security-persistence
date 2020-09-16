package org.softnetwork.security.service

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import akka.http.scaladsl.model.headers.{Cookie, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}

import akka.http.scaladsl.testkit.PersistenceTypedRouteTestKit

import de.heikoseeberger.akkahttpjson4s.Json4sSupport

import org.scalatest.wordspec.AnyWordSpecLike

import org.softnetwork.akka.serialization._

import org.softnetwork.security.config.Settings
import org.softnetwork.security.handlers.{MockBasicAccountTypeKey, AccountKeyDao, MockGenerator}

import org.softnetwork.security.message._
import org.softnetwork.security.serialization._

import org.softnetwork.security.model.{AccountView, AccountStatus}

import org.softnetwork.security.persistence.typed.MockBasicAccountBehavior
import org.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior

/**
  * Created by smanciot on 22/03/2018.
  */
class MainRoutesSpec extends AnyWordSpecLike with PersistenceTypedRouteTestKit with Json4sSupport {

  implicit def formats = securityFormats

  lazy val mainRoutes: MainRoutes = new MainRoutes()(typedSystem()) with MockBasicAccountTypeKey

  private val username = "smanciot"

  private val firstName = Some("Stephane")

  private val lastName = Some("Manciot")

  private val email = "stephane.manciot@gmail.com"

  private val gsm = "33660010203"

  private val password = "Changeit1"

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      MockBasicAccountBehavior.init(context.system)
      SessionRefreshTokenBehavior.init(context.system)
      Behaviors.empty
    }
  }

  "MainRoutes" should {
    "contain a healthcheck path" in {
      Get("/api/healthcheck") ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "SignUp" should {
    "fail if confirmed password does not match password" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(username, password, Some("fake"))) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe PasswordsNotMatched.message
      }
    }
    "work with username" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(username, password, None, firstName, lastName))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail if username already exists" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(username, password))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldBe LoginAlreadyExists.message
      }
    }
    "work with email" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(email, password, None, firstName, lastName))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Inactive
      }
    }
    "fail if email already exists" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(email, password))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
    "work with gsm" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(gsm, password, None, firstName, lastName))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail if gsm already exists" in {
      Post(s"/api/${Settings.Path}/signUp", SignUp(gsm, password))  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[AccountErrorMessage].message shouldEqual LoginAlreadyExists.message
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      Post(s"/api/${Settings.Path}/login", Login(username, password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "work with matching email and password" in {
      AccountKeyDao.lookupAccount(email) match {
        case Some(uuid) =>
          Get(s"/api/${Settings.Path}/activate", Activate(MockGenerator.computeToken(uuid))) ~> mainRoutes.routes
          Post(s"/api/${Settings.Path}/login", Login(email, password)) ~> mainRoutes.routes ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[AccountView].status shouldBe AccountStatus.Active
          }
        case _          => fail()
      }
    }
    "work with matching gsm and password" in {
      Post(s"/api/${Settings.Path}/login", Login(gsm, password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldBe AccountStatus.Active
      }
    }
    "fail with unknown username" in {
      Post(s"/api/${Settings.Path}/login", Login("fake", password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown email" in {
      Post(s"/api/${Settings.Path}/login", Login("fake@gmail.com", password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unknown gsm" in {
      Post(s"/api/${Settings.Path}/login", Login("0102030405", password)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching username and password" in {
      Post(s"/api/${Settings.Path}/login", Login(username, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching email and password" in {
      Post(s"/api/${Settings.Path}/login", Login(email, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "fail with unmatching gsm and password" in {
      Post(s"/api/${Settings.Path}/login", Login(gsm, "fake")) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual LoginAndPasswordNotMatched.message
      }
    }
    "disable account after n login failures" in {
      Post(s"/api/${Settings.Path}/login", Login(gsm, password)) ~> mainRoutes.routes  // reset number of failures
      val failures = (0 to Settings.MaxLoginFailures) // max number of failures + 1
          .map(_ => Post(s"/api/${Settings.Path}/login", Login(gsm, "fake")) ~> mainRoutes.routes )
      failures.last ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[AccountErrorMessage].message shouldEqual AccountDisabled.message
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      var _headers: Seq[HttpHeader] = Seq.empty
      Post(s"/api/${Settings.Path}/verificationCode", SendVerificationCode(gsm)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
        _headers = headers
      }
      Post(s"/api/${Settings.Path}/resetPassword", ResetPassword(MockGenerator.code, password))
        .withHeaders(extractCookies(_headers):_*)  ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Logout" should {
    "work" in {
      var _headers: Seq[HttpHeader] = Seq.empty
      Post(s"/api/${Settings.Path}/login", Login(gsm, password, refreshable = true)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
        _headers = headers
      }
      Post(s"/api/${Settings.Path}/logout").withHeaders(extractCookies(_headers):_*) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      var _headers: Seq[HttpHeader] = Seq.empty
      Post(s"/api/${Settings.Path}/login", Login(gsm, password, refreshable = true)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
        _headers = headers
      }
      Post(s"/api/${Settings.Path}/unsubscribe").withHeaders(extractCookies(_headers):_*) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccountView].status shouldEqual AccountStatus.Deleted
      }
    }
  }

  "SendVerificationCode" should {
    "work with email" in {
      Post(s"/api/${Settings.Path}/verificationCode", SendVerificationCode(email)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "work with gsm" in {
      Post(s"/api/${Settings.Path}/verificationCode", SendVerificationCode(gsm)) ~> mainRoutes.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  def extractCookies(headers: Seq[HttpHeader]): Seq[HttpHeader] = {
    headers.filter((header) => {
      val name = header.lowercaseName()
      log.info(s"$name:${header.value}")
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
