package org.softnetwork.security.service

import _root_.akka.actor.typed.Behavior
import _root_.akka.actor.typed.scaladsl.Behaviors

import _root_.akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork._

import org.softnetwork.notification.model.Platform

import org.softnetwork.security.config.Settings
import org.softnetwork.security.handlers.MockBasicAccountTypeKey

import org.softnetwork.security.message._

import org.softnetwork.security.model._

import org.softnetwork.security.persistence.typed.MockBasicAccountBehavior

import org.softnetwork.security.handlers.MockGenerator._

/**
  * Created by smanciot on 18/04/2020.
  */
class AccountServiceSpec extends AccountService
  with MockBasicAccountTypeKey with AnyWordSpecLike with PersistenceTypedActorTestKit {

  implicit lazy val system = typedSystem()

  private val username = "test"

  private val firstname = "firstname"

  private def email(user: String = username) = s"$user@gmail.com"

  private def generateUuid(key: String) = s"$key-uuid"

  private val gsm = "33660010203"

  private val gsm2 = "33660020304"

  private val regId = "regId"

  private val pwd = "Changeit1"

  private val newPassword = "Changeit2"

  private val usernameUuid: String = generateUUID(Some(username))

  private val emailUuid: String = generateUUID(Some(email()))

  private val gsmUuid: String = generateUUID(Some(gsm))

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      MockBasicAccountBehavior.init(context.system)
      Behaviors.empty
    }
  }

  "SignUp" should {
    "fail if confirmed password does not match password" in {
      run("PasswordsNotMatched", SignUp(username, pwd, Some("fake"))) match {
        case PasswordsNotMatched =>
        case _ => fail()
      }
    }

    "work with username" in {
      run(usernameUuid, SignUp(username, pwd)) match {
        case r: AccountCreated => r.account.status shouldBe AccountStatus.Active
        case _ => fail()
      }
    }

    "fail if username already exists" in {
      run(usernameUuid, SignUp(username, pwd)) match {
        case LoginAlreadyExists =>
        case _ => fail()
      }
    }

    "work with email" in {
      run(emailUuid, SignUp(email(), pwd)) match {
        case r: AccountCreated => r.account.status shouldBe AccountStatus.Inactive
        case _ => fail()
      }
    }

    "fail if email already exists" in {
      run(emailUuid, SignUp(email(), pwd)) match {
        case LoginAlreadyExists =>
        case _ => fail()
      }
    }

    "work with gsm" in {
      run(gsmUuid, SignUp(gsm, pwd)) match {
        case r: AccountCreated => r.account.status shouldBe AccountStatus.Active
        case _ => fail()
      }
    }

    "fail if gsm already exists" in {
      run(gsmUuid, SignUp(gsm, pwd)) match {
        case LoginAlreadyExists =>
        case _ => fail()
      }
    }

  }

  "Activation" should {
    "fail if account is not found" in {
      ?? (generateUUID(Some("fake")), Activate("fake")) match {
        case AccountNotFound =>
        case _ => fail()
      }
    }

    "fail if account is not inactive" in {
      ? (usernameUuid, Activate("fake")) match {
        case IllegalStateError =>
        case other => fail(other.toString)
      }
    }

    "fail if token does not match activation token" in {
      ? (emailUuid, Activate("fake")) match {
        case InvalidToken =>
        case other => fail(other.toString)
      }
    }

    "work if account is inactive and token matches activation token" in {
      val token = computeToken(emailUuid)
      run(token, Activate(token)) match {
        case e: AccountActivated =>
          import e.account._
          verificationToken.isEmpty shouldBe true
          status shouldBe AccountStatus.Active
          uuid shouldBe emailUuid
        case _ => fail()
      }
    }
  }

  "Login" should {
    "work with matching username and password" in {
      run(usernameUuid, Login(username, pwd)) match {
        case _: LoginSucceededResult =>
        case _ => fail()
      }
    }
    "fail with unknown username" in {
      run(generateUUID(Some("fake")), Login("fake", pwd)) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "fail with unmatching username and password" in {
      run(usernameUuid, Login(username, "fake")) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "work with matching email and password" in {
      run(emailUuid, Login(email(), pwd)) match {
        case _: LoginSucceededResult =>
        case _ => fail()
      }
    }
    "fail with unknown email" in {
      run(generateUUID(Some(email("fake"))), Login(email("fake"), pwd)) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "fail with unmatching email and password" in {
      run(emailUuid, Login(email(), "fake")) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "work with matching gsm and password" in {
      run(gsmUuid, Login(gsm, pwd)) match {
        case _: LoginSucceededResult =>
        case _ => fail()
      }
    }
    "fail with unknown gsm" in {
      run(generateUUID(Some("0123456789")), Login("0123456789", pwd)) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "fail with unmatching gsm and password" in {
      run(gsmUuid, Login(gsm, "fake")) match {
        case LoginAndPasswordNotMatched =>
        case _ => fail()
      }
    }
    "disable account after n login failures" in {
      this !(gsmUuid, Login(gsm, pwd))
      val failures = (0 to Settings.MaxLoginFailures) // max number of failures + 1
        .map(_ => run(gsmUuid, Login(gsm, "fake")))
      failures.last match {
        case AccountDisabled =>
        case _ => fail()
      }
    }
  }

  "SendVerificationCode" should {
    "work with gsm" in {
      run(gsmUuid, SendVerificationCode(gsm)) match {
        case VerificationCodeSent =>
        case _ => fail()
      }
    }
    "work with email" in {
      run(emailUuid, SendVerificationCode(email())) match {
        case VerificationCodeSent =>
        case _ => fail()
      }
    }
    "fail with username" in {
      run(usernameUuid, SendVerificationCode(username)) match {
        case InvalidPrincipal =>
        case _ => fail()
      }
    }
  }

  "SendResetPasswordToken" should {
    "work with gsm" in {
      run(gsmUuid, SendResetPasswordToken(gsm)) match {
        case ResetPasswordTokenSent =>
        case _ => fail()
      }
    }
    "work with email" in {
      run(emailUuid, SendResetPasswordToken(email())) match {
        case ResetPasswordTokenSent =>
        case _ => fail()
      }
    }
    "fail with username" in {
      run(usernameUuid, SendResetPasswordToken(username)) match {
        case InvalidPrincipal =>
        case _ => fail()
      }
    }
  }

  "CheckResetPasswordToken" should {
    "work when a valid token has been generated for the corresponding account" in {
      val token = computeToken(emailUuid)
      run(token, CheckResetPasswordToken(token)) match {
        case ResetPasswordTokenChecked =>
        case _ => fail()
      }
    }
    "fail when the token does not exist" in {
      run("fake", CheckResetPasswordToken("fake")) match {
        case TokenNotFound =>
        case _ => fail()
      }
    }
  }

  "ResetPassword" should {
    "work" in {
      val token = computeToken(emailUuid)
      run(token, ResetPassword(token, newPassword)) match {
        case _: PasswordReseted =>
        case _ => fail()
      }
    }
  }

  "UpdatePassword" should {
    "work" in {
      run(usernameUuid, UpdatePassword(username, pwd, newPassword)) match {
        case _: PasswordUpdated =>
        case _ => fail()
      }
    }
  }

  "Device" should {
    "be registered" in {
      run(generateUUID(Some(email("DeviceRegistration"))), SignUp(email("DeviceRegistration"), pwd)) match {
        case r: AccountCreated =>
          r.account.status shouldBe AccountStatus.Inactive
          run(generateUUID(Some(email("DeviceRegistration"))), RegisterDevice(
            generateUUID(Some(email("DeviceRegistration"))), DeviceRegistration(regId, Platform.IOS))) match {
              case DeviceRegistered =>
              case _ => fail()
          }
        case _ => fail()
      }
    }

    "be unregistered" in {
      run(generateUUID(Some(email("DeviceRegistration"))),
        UnregisterDevice(generateUUID(Some(email("DeviceRegistration"))), regId)) match {
        case DeviceUnregistered =>
        case _ => fail()
      }
    }
  }

  "Unsubscribe" should {
    "work" in {
      run(generateUUID(Some(email("DeviceRegistration"))), Unsubscribe("DeviceRegistration")) match {
        case _: AccountDeleted =>
        case _ => fail()
      }
    }
  }

  "Logout" should {
    "work" in {
      run(usernameUuid, Logout) match {
        case LogoutSucceeded =>
        case _ => fail()
      }
    }
  }

  "UpdateProfile" should {
    "work" in {
      val uuid = usernameUuid
      val profile =
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
      run(uuid, UpdateProfile(uuid, profile)) match {
        case ProfileUpdated =>
        case _ => fail()
      }
    }
  }

  "SwitchProfile" should {
    "work" in {
      val uuid = usernameUuid
      run(uuid, SwitchProfile(uuid, "test")) match {
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
      val uuid = usernameUuid
      run(uuid, LoadProfile(uuid, Some("test"))) match {
        case r: ProfileLoaded =>
          import r._
          profile.firstName shouldBe firstname
          profile.phoneNumber shouldBe Some(gsm2)
        case _ => fail()
      }
    }
  }

}