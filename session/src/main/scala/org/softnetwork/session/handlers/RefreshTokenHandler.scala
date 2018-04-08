package org.softnetwork.session.handlers

import akka.actor.ActorRef
import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData, RefreshTokenStorage}
import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.akka.http.Handler
import org.softnetwork.session.message._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by smanciot on 22/03/2018.
  */
class RefreshTokenHandler[T](refreshTokenStateActor: ActorRef) extends Handler[RefreshTokenCommand, RefreshTokenResult]
  with RefreshTokenStorage[T]
  with StrictLogging{
  override val actor = refreshTokenStateActor

  override val defaultAtMost = 10.second

  override def lookup(selector: String): Future[Option[RefreshTokenLookupResult[T]]] = {
    logger.info(s"Looking up token for selector: $selector")
    Future.successful(
      handle(LookupRefreshToken(selector)).asInstanceOf[LookupRefreshTokenResult[T]].data
    )
  }

  override def schedule[S](after: Duration)(op: => Future[S]): Unit = {
    logger.info("Running scheduled operation immediately")
    op
    Future.successful(())
  }

  override def store(data: RefreshTokenData[T]): Future[Unit] = {
    logger.info(s"Storing token for " +
      s"selector: ${data.selector}, " +
      s"user: ${data.forSession}, " +
      s"expires: ${data.expires}, " +
      s"now: ${System.currentTimeMillis()}"
    )
    handle(StoreRefreshToken(data))
    Future.successful()
  }

  override def remove(selector: String): Future[Unit] = {
    logger.info(s"Removing token for selector: $selector")
    handle(RemoveRefreshToken(selector))
    Future.successful()
  }
}