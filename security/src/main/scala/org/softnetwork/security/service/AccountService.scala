package org.softnetwork.security.service

import akka.http.scaladsl.model.{StatusCodes, HttpResponse}
import akka.http.scaladsl.server.Directives
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._

import com.typesafe.scalalogging.StrictLogging

import de.heikoseeberger.akkahttpjson4s.Json4sSupport

import org.softnetwork.akka.http.{DefaultComplete, EntityService}
import org.softnetwork.akka.http._

import org.softnetwork.akka.persistence.typed.CommandTypeKey
import org.softnetwork.akka.serialization._

import org.softnetwork.security.config.Settings

import org.softnetwork.security.handlers.AccountHandler
import org.softnetwork.security.message._
import org.softnetwork.security.model._
import org.softnetwork.security.serialization._
import org.softnetwork.session.Session

import org.softnetwork.session.service.SessionService

import scala.concurrent.duration.FiniteDuration

/**
  * Created by smanciot on 23/04/2020.
  */
trait AccountService extends EntityService[AccountCommand, AccountCommandResult]
  with AccountHandler
  with SessionService
  with Directives
  with DefaultComplete
  with Json4sSupport
  with StrictLogging {_: CommandTypeKey[AccountCommand] =>

  implicit def atMost: FiniteDuration = defaultAtMost

  import Session._

  import org.softnetwork._

  implicit def formats = securityFormats

  val route = {
    pathPrefix(Settings.Path){
      signUp ~
        login ~
        activate ~
        logout ~
        verificationCode ~
        resetPasswordToken ~
        resetPassword ~
        unsubscribe ~
        device ~
        password
    }
  }

  lazy val signUp = path("signUp") {
    post {
      entity(as[SignUp]) { signUp =>
        // execute signUp
        run(generateUUID(Some(signUp.login)), signUp) match {
          case r: AccountCreated =>
            lazy val completion = complete(HttpResponse(status = StatusCodes.Created, entity = r.account.view))
            if(!Settings.ActivationEnabled){
              val account = r.account
              // create a new session
              sessionToDirective(Session(account.uuid, refreshable = false))(ec) {
                // create a new anti csrf token
                setNewCsrfToken(checkHeader) {
                  completion
                }
              }
            }
            else{
              completion
            }
          case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                          => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val activate = path("activate") {
    get {
      entity(as[Activate]) { activate =>
        // execute activate
        run(activate.token, activate) match {
          case r: AccountActivated =>
            val account = r.account
            // create a new session
            sessionToDirective(Session(account.uuid, refreshable = false))(ec) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK, entity = account.view))
              }
            }
          case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                          => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val login = path("login" | "signIn") {
    post {
      entity(as[Login]) { login =>
        // execute Login
        run(generateUUID(Some(login.login)), login) match {
          case r: LoginSucceededResult =>
            val account = r.account
            // create a new session
            val session = Session(account.uuid, refreshable = false/** FIXME login.refreshable **/)
            session += (Session.adminKey, account.isAdmin)
            sessionToDirective(session)(ec) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(StatusCodes.OK, entity = account.view))
              }
            }
          case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.Unauthorized, entity = error))
          case _                          => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val logout = path("logout" | "signOut") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        _requiredSession(ec) { session =>
          run(session.id, Logout) match {
            case _: LogoutSucceeded.type    =>
              // invalidate session
              _invalidateSession(ec) {
                complete(HttpResponse(StatusCodes.OK, entity = Map[String, String]()))
              }
            case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _                          => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val unsubscribe = path("unsubscribe") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        _requiredSession(ec) { session =>
          run(session.id, Unsubscribe(session.id)) match {
            case r: AccountDeleted =>
              // invalidate session
              _invalidateSession(ec) {
                complete(HttpResponse(status = StatusCodes.OK, entity = r.account.view))
              }
            case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _                          => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val verificationCode = path("verificationCode") {
    post {
      entity(as[SendVerificationCode]) { verificationCode =>
        run(generateUUID(Some(verificationCode.principal)), verificationCode) match {
          case _: VerificationCodeSent.type  =>
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(status = StatusCodes.OK))
            }
          case error: AccountErrorMessage   => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                            => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val resetPasswordToken = pathPrefix("resetPasswordToken") {
    pathSuffix(Segment) { token =>
      get{
        run(token, CheckResetPasswordToken(token)) match {
          case _: ResetPasswordTokenChecked.type  =>
            // create a new anti csrf token
            setNewCsrfToken(checkHeader) {
              complete(HttpResponse(status = StatusCodes.OK))
            }
          case error: AccountErrorMessage   => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                            => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    } ~ post {
      entity(as[SendResetPasswordToken]) { resetPasswordToken =>
        run(generateUUID(Some(resetPasswordToken.principal)), resetPasswordToken) match {
          case _: ResetPasswordTokenSent.type  => complete(HttpResponse(status = StatusCodes.OK))
          case error: AccountErrorMessage      => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
          case _                               => complete(HttpResponse(StatusCodes.BadRequest))
        }
      }
    }
  }

  lazy val resetPassword = path("resetPassword") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        entity(as[ResetPassword]) { reset =>
          run(reset.token, reset) match {
            case r: PasswordReseted         =>
              // create a new session
              sessionToDirective(Session(r.uuid, refreshable = false))(ec) {
                complete(HttpResponse(status = StatusCodes.OK))
              }
            case error: AccountErrorMessage => complete(HttpResponse(StatusCodes.BadRequest, entity = error))
            case _                          => complete(HttpResponse(StatusCodes.BadRequest))
          }
        }
      }
    }
  }

  lazy val device = pathPrefix("device") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        post{
          entity(as[DeviceRegistration]){registration =>
            run(
              session.id,
              RegisterDevice(
                session.id,
                registration
              )
            ) match {
              case DeviceRegistered => complete(HttpResponse(status = StatusCodes.OK))
              case AccountNotFound  => complete(HttpResponse(status = StatusCodes.NotFound))
              case _                => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        } ~
          pathPrefix(Segment) {regId =>
            delete{
              run(session.id, UnregisterDevice(session.id, regId)) match {
                case DeviceUnregistered         => complete(HttpResponse(status = StatusCodes.OK))
                case AccountNotFound            => complete(HttpResponse(status = StatusCodes.NotFound))
                case DeviceRegistrationNotFound => complete(HttpResponse(status = StatusCodes.NotFound))
                case _                          => complete(HttpResponse(StatusCodes.BadRequest))
              }
            }
          }
      }
    }
  }

  lazy val password = pathPrefix("password"){
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      // check if a session exists
      _requiredSession(ec) { session =>
        post {
          entity(as[PasswordData]) {data =>
            import data._
            run(session.id, UpdatePassword(session.id, oldPassword, newPassword, confirmedPassword)) match {
              case _: PasswordUpdated => complete(HttpResponse(status = StatusCodes.OK))
              case error: AccountErrorMessage =>
                complete(
                  HttpResponse(
                    StatusCodes.BadRequest,
                    entity = error
                  )
                )
              case _ => complete(HttpResponse(StatusCodes.BadRequest))
            }
          }
        }
      }
    }
  }
}
