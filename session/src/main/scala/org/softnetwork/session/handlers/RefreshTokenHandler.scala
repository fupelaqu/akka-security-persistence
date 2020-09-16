package org.softnetwork.session.handlers

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData, RefreshTokenStorage}
import org.softnetwork.akka.handlers.EntityHandler
import org.softnetwork.akka.persistence.typed.CommandTypeKey
import org.softnetwork.session.Session
import org.softnetwork.session.message._
import org.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
  * Created by smanciot on 14/04/2020.
  */
trait RefreshTokenHandler[T] extends EntityHandler[RefreshTokenCommand, RefreshTokenResult]
  with RefreshTokenStorage[T]{_: CommandTypeKey[RefreshTokenCommand] =>
}

trait SessionRefreshTokenTypeKey extends CommandTypeKey[RefreshTokenCommand] {
  override def TypeKey(implicit tTag: ClassTag[RefreshTokenCommand]): EntityTypeKey[RefreshTokenCommand] =
    SessionRefreshTokenBehavior.TypeKey
}

trait SessionRefreshTokenHandler extends RefreshTokenHandler[Session] with SessionRefreshTokenTypeKey

trait RefreshTokenDao[T] extends RefreshTokenStorage[T]{_: RefreshTokenHandler[T] =>

  override def lookup(selector: String): Future[Option[RefreshTokenLookupResult[T]]] = {
    logger.info(s"Looking up token for selector: $selector")
    Future.successful(
      (this !? LookupRefreshToken(selector)).asInstanceOf[LookupRefreshTokenResult[T]].data
    )
  }

  override def store(data: RefreshTokenData[T]): Future[Unit] = {
    logger.info(s"Storing token for " +
      s"selector: ${data.selector}, " +
      s"user: ${data.forSession}, " +
      s"expires: ${data.expires}, " +
      s"now: ${System.currentTimeMillis()}"
    )
    this !? StoreRefreshToken(data)
    Future.successful((): Unit)
  }

  override def remove(selector: String): Future[Unit] = {
    logger.info(s"Removing token for selector: $selector")
    this !? RemoveRefreshToken(selector)
    Future.successful((): Unit)
  }

  override def schedule[S](after: Duration)(op: => Future[S]): Unit = {
    logger.info("Running scheduled operation immediately")
    op
    Future.successful(())
  }
}

trait SessionRefreshTokenDao extends RefreshTokenDao[Session] with SessionRefreshTokenHandler

object SessionRefreshTokenDao extends SessionRefreshTokenDao
