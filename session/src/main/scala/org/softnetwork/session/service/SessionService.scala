package org.softnetwork.session.service

import com.softwaremill.session.RefreshTokenStorage
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import org.softnetwork.session.Session
import org.softnetwork.session.handlers.SessionRefreshTokenDao

import scala.concurrent.ExecutionContext

/**
  * Created by smanciot on 05/07/2018.
  */
trait SessionService {

  import Session._

  implicit val refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao

  protected def sessionToDirective(session: Session)(implicit ec: ExecutionContext) =
    setSession(if(session.refreshable)refreshable else oneOff, usingCookies, session)

  protected def _requiredSession(implicit ec: ExecutionContext) = requiredSession(refreshable, usingCookies)

  protected def _invalidateSession(implicit ec: ExecutionContext) = invalidateSession(refreshable, usingCookies)

  protected def _optionalSession(implicit ec: ExecutionContext) = optionalSession(refreshable, usingCookies)
}
