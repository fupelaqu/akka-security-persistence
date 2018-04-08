package org.softnetwork.security.service

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.akka.http.Implicits._
import org.softnetwork.akka.http.{DefaultComplete, Service}
import org.softnetwork.security.config.Settings
import org.softnetwork.security.handlers.AccountHandler
import org.softnetwork.security.message._
import org.softnetwork.security.model.Account
import org.softnetwork.session.Session
import org.softnetwork.session.handlers.SessionRefreshTokenHandler

import scala.concurrent.ExecutionContext

/**
  * Created by smanciot on 20/03/2018.
  */
class AccountService(
  override val handler: AccountHandler,
  val sessionRefreshTokenHandler: SessionRefreshTokenHandler
)(implicit ec: ExecutionContext = ActorSystemLocator().dispatcher)
  extends Service[AccountCommand, AccountCommandResult]
  with Directives
  with DefaultComplete
  with Json4sSupport
  with StrictLogging{

  import Session._

  implicit val refreshTokenStorage = sessionRefreshTokenHandler

  private def sessionToDirective(session: Session) = setSession(if(session.refreshable)refreshable else oneOff, usingCookies, session)

  private val _requiredSession = requiredSession(refreshable, usingCookies)

  private val _invalidateSession = invalidateSession(refreshable, usingCookies)

  val route = {
    pathPrefix(Settings.Path){
      signIn ~ login ~ logout ~ signOut
    }
  }

  lazy val signIn = path("signIn") {
    post {
      entity(as[SignIn]) { signIn =>
        // execute signIn
        run(signIn) match {
          case r: AccountCreated[_]       =>
            complete(HttpResponse(status = Created, entity = accountEntity(r.account)))
          case error: AccountErrorMessage => accountError(error)
          case _                          => complete(HttpResponse(BadRequest))
        }
      }
    }
  }

  lazy val login = path("login") {
    post {
      entity(as[Login]) { login =>
        // execute Login
        run(login) match {
          case r: LoginSucceeded[_]      =>
            val account = r.account
            // create a new session
            sessionToDirective(Session(account.uuid, login.refreshable)) {
              // create a new anti csrf token
              setNewCsrfToken(checkHeader) {
                complete(HttpResponse(Accepted, entity = accountEntity(account)))
              }
            }
          case error: AccountErrorMessage => accountError(error)
          case _                          => complete(HttpResponse(BadRequest))
        }
      }
    }
  }

  lazy val logout = path("logout") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        _requiredSession { session =>
          run(Logout) match {
            case _: LogoutSucceeded.type    =>
              // invalidate session
              _invalidateSession {
                complete(HttpResponse(OK, entity = HttpEntity(`application/json`, serialization.write(Map[String, String]()))))
              }
            case error: AccountErrorMessage => accountError(error)
            case _                          => complete(HttpResponse(BadRequest))
          }
        }
      }
    }
  }

  lazy val signOut = path("signOut") {
    // check anti CSRF token
    randomTokenCsrfProtection(checkHeader) {
      post {
        // check if a session exists
        _requiredSession { session =>
          run(SignOut(session.id)) match {
            case r: AccountDeleted[_]       =>
              // invalidate session
              _invalidateSession {
                complete(HttpResponse(status = OK, entity = accountEntity(r.account)))
              }
            case error: AccountErrorMessage => accountError(error)
            case _                          => complete(HttpResponse(BadRequest))
          }
        }
      }
    }
  }

  private def accountEntity(account: Account) = HttpEntity(`application/json`, serialization.write(account.view))

  private def accountError(error: AccountErrorMessage) = {
    complete(HttpResponse(BadRequest, entity = HttpEntity(`application/json`, serialization.write(Map("message" -> error.message)))))
  }

}
