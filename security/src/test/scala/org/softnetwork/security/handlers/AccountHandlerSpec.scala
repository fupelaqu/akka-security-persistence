package org.softnetwork.security.handlers

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.notification.model.Platform
import org.softnetwork.security.config.Settings
import org.softnetwork.security.message._
import org.softnetwork.security.model._
import org.softnetwork.security.persistence.typed.MockBasicAccountBehavior

/**
  * Created by smanciot on 18/04/2020.
  */
class AccountHandlerSpec extends AccountHandler
  with MockBasicAccountTypeKey with AnyWordSpecLike with PersistenceTypedActorTestKit {

  implicit lazy val system = typedSystem()

  import MockGenerator._

  private val username = "test"

  private val firstname = "firstname"

  private def email(user: String = username) = s"$user@gmail.com"

  private def generateUuid(key: String) = s"$key-uuid"

  private val gsm = "33660010203"

  private val gsm2 = "33660020304"

  private val regId = "regId"

  private val password = "Changeit1"

  private val newPassword = "Changeit2"

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      MockBasicAccountBehavior.init(context.system)
      Behaviors.empty
    }
  }

  import org.softnetwork._

  private val usernameUuid: String = generateUUID(Some(username))

  private val emailUuid: String = generateUUID(Some(email()))

  private val gsmUuid: String = generateUUID(Some(gsm))

  "SignUp" should {
    "fail if confirmed password does not match password" in {
      this ?? (usernameUuid, SignUp(username, password, Some("fake"))) match {
        case PasswordsNotMatched =>
        case _ => fail()
      }
    }

    "work with username" in {
      this ?? (usernameUuid, SignUp(username, password)) match {
        case r: AccountCreated =>
          import r._
          account.status shouldBe AccountStatus.Active
          account.email.isDefined shouldBe false
          account.gsm.isDefined shouldBe false
          account.username.isDefined shouldBe true
        case other => fail(other.getClass)
      }
    }

    "fail if username already exists" in {
      this ?? (usernameUuid, SignUp(username, password)) match {
        case LoginAlreadyExists =>
        case _ => fail()
      }
    }

    "work with email" in {
      this ?? (emailUuid, SignUp(email(), password)) match {
        case r: AccountCreated =>
          import r._
          account.status shouldBe AccountStatus.Inactive
          account.email.isDefined shouldBe true
          account.gsm.isDefined shouldBe false
          account.username.isDefined shouldBe false
        case other => fail(other.toString)
      }
    }

    "fail if email already exists" in {
      this ?? (emailUuid, SignUp(email(), password)) match {
        case LoginAlreadyExists =>
        case _ => fail()
      }
    }

    "work with gsm" in {
      this ?? (gsmUuid, SignUp(gsm, password)) match {
        case r: AccountCreated =>
          import r._
          account.status shouldBe AccountStatus.Active
          account.email.isDefined shouldBe false
          account.gsm.isDefined shouldBe true
          account.username.isDefined shouldBe false
        case _ => fail()
      }
    }

    "fail if gsm already exists" in {
      this ?? (gsmUuid, SignUp(gsm, password)) match {
        case LoginAlreadyExists =>
        case _ => fail()
      }
    }

  }

  "Activation" should {
    "fail if account is not found" in {
      this ?? (generateUUID(Some("fake")), Activate("fake")) match {
        case AccountNotFound =>
        case _ => fail()
      }
    }

    "fail if account is not inactive" in {
      this ? (usernameUuid, Activate("fake")) match {
        case IllegalStateError =>
        case _ => fail()
      }
    }

    "fail if token does not match activation token" in {
      this ? (emailUuid, Activate("fake")) match {
        case InvalidToken =>
        case other => fail(other.toString)
      }
    }

    "work if account is inactive and token matches activation token" in {
      val token = computeToken(emailUuid)
      this ?? (token, Activate(token)) match {
        case e: AccountActivated =>
          import e.account._
          verificationToken.isEmpty shouldBe true
          status shouldBe AccountStatus.Active
          uuid shouldBe emailUuid
        case other => fail(other.toString)
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      this ?? (usernameUuid, Login(username, password)) match {
        case _: LoginSucceededResult =>
        case _ => fail()
      }
    }
    "fail with unknown username" in {
      this ?? (generateUUID(Some("fake")), Login("fake", password)) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "fail with unmatching username and password" in {
      this ?? (usernameUuid, Login(username, "fake")) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "work with matching email and password" in {
      this ?? (emailUuid, Login(email(), password)) match {
        case _: LoginSucceededResult =>
        case _ => fail()
      }
    }
    "fail with unknown email" in {
      this ?? (generateUUID(Some(email("fake"))), Login(email("fake"), password)) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "fail with unmatching email and password" in {
      this ?? (emailUuid, Login(email(), "fake")) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "work with matching gsm and password" in {
      this ?? (gsmUuid, Login(gsm, password)) match {
        case _: LoginSucceededResult =>
        case _ => fail()
      }
    }
    "fail with unknown gsm" in {
      this ?? (generateUUID(Some("0123456789")), Login("0123456789", password)) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "fail with unmatching gsm and password" in {
      this ?? (gsmUuid, Login(gsm, "fake")) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "disable account after n login failures" in {
      this !(gsmUuid, Login(gsm, password))
      val failures = (0 to Settings.MaxLoginFailures) // max number of failures + 1
        .map(_ => this ?? (gsmUuid, Login(gsm, "fake")))
      failures.last match {
        case AccountDisabled =>
        case _ => fail()
      }
    }
  }

  "SendVerificationCode" should {
    "work with gsm" in {
      this ?? (gsmUuid, SendVerificationCode(gsm)) match {
        case VerificationCodeSent =>
        case _ => fail()
      }
    }
    "work with email" in {
      this ?? (emailUuid, SendVerificationCode(email())) match {
        case VerificationCodeSent =>
        case _ => fail()
      }
    }
    "fail with username" in {
      this ?? (usernameUuid, SendVerificationCode(username)) match {
        case InvalidPrincipal =>
        case _ => fail()
      }
    }
  }

  "SendResetPasswordToken" should {
    "work with gsm" in {
      this ?? (gsmUuid, SendResetPasswordToken(gsm)) match {
        case ResetPasswordTokenSent =>
        case _ => fail()
      }
    }
    "work with email" in {
      this ?? (emailUuid, SendResetPasswordToken(email())) match {
        case ResetPasswordTokenSent =>
        case _ => fail()
      }
    }
    "fail with username" in {
      this ?? (usernameUuid, SendResetPasswordToken(username)) match {
        case InvalidPrincipal =>
        case _ => fail()
      }
    }
  }

  "CheckResetPasswordToken" should {
    "work when a valid token has been generated for the corresponding account" in {
      val token = computeToken(emailUuid)
      this ?? (token, CheckResetPasswordToken(token)) match {
        case ResetPasswordTokenChecked =>
        case _ => fail()
      }
    }
    "fail when the token does not exist" in {
      this ?? ("fake", CheckResetPasswordToken("fake")) match {
        case TokenNotFound =>
        case _ => fail()
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      val token = computeToken(emailUuid)
      this ?? (token, ResetPassword(token, newPassword)) match {
        case _: PasswordReseted =>
        case _ => fail()
      }
    }
  }

  "UpdatePassword" should {
    "work" in {
      this ?? (usernameUuid, UpdatePassword(username, password, newPassword)) match {
        case _: PasswordUpdated =>
        case _ => fail()
      }
    }
  }

  "Device" should {
    "be registered" in {
      this ?? (generateUUID(Some(email("DeviceRegistration"))), SignUp(email("DeviceRegistration"), password)) match {
        case r: AccountCreated =>
          r.account.status shouldBe AccountStatus.Inactive
          this ?? (generateUUID(Some(email("DeviceRegistration"))), RegisterDevice(
            generateUUID(Some(email("DeviceRegistration"))), DeviceRegistration(regId, Platform.IOS))) match {
              case DeviceRegistered =>
              case _ => fail()
          }
        case _ => fail()
      }
    }

    "be unregistered" in {
      this ?? (
        generateUUID(Some(email("DeviceRegistration"))),
        UnregisterDevice(generateUUID(Some(email("DeviceRegistration"))), regId)) match {
        case DeviceUnregistered =>
        case _ => fail()
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      this ?? (generateUUID(Some(email("DeviceRegistration"))), Unsubscribe(
        generateUUID(Some(email("DeviceRegistration"))))) match {
        case _: AccountDeleted =>
        case _ => fail()
      }
    }
  }

  "Logout" should {
    "work" in {
      this ?? (usernameUuid, Logout) match {
        case LogoutSucceeded =>
        case _ => fail()
      }
    }
  }

  "UpdateProfile" should {
    "work" in {
      val uuid = usernameUuid
      this ?? (
        uuid,
        UpdateProfile(
          uuid,
          BasicAccountProfile.defaultInstance
            .withUuid(uuid)
            .withName("test")
            .copyWithDetails(
              Some(
                BasicAccountDetails.defaultInstance
                  .withUuid(uuid)
                  .withFirstName(firstname)
                  .withPhoneNumber(gsm2)
              )
            )
        )) match {
        case ProfileUpdated =>
        case _ => fail()
      }
    }
  }

  "SwitchProfile" should {
    "work" in {
      this ?? (usernameUuid, SwitchProfile(usernameUuid, "test")) match {
        case r: ProfileSwitched =>
          r.profile match {
            case Some(s2) =>
              s2.firstName shouldBe firstname
              s2.phoneNumber shouldBe Some(gsm2)
            case _ => fail()
          }
        case _ => fail()
      }
    }
  }

  "LoadProfile" should {
    "work" in {
      this ?? (usernameUuid, LoadProfile(usernameUuid, Some("test"))) match {
        case r: ProfileLoaded =>
          import r._
          profile.firstName shouldBe firstname
          profile.phoneNumber shouldBe Some(gsm2)
        case _ => fail()
      }
    }
  }

}