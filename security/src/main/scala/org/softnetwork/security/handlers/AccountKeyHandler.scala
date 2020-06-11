package org.softnetwork.security.handlers

import akka.actor.typed.ActorSystem

import org.softnetwork.akka.handlers.EntityHandler

import org.softnetwork.security.message._

import org.softnetwork.security.persistence.typed.AccountKeyBehavior

import org.softnetwork._

/**
  * Created by smanciot on 17/04/2020.
  */
trait AccountKeyHandler extends EntityHandler[AccountKeyCommand, AccountKeyCommandResult] with AccountKeyBehavior {

  override protected def command2Request(command: AccountKeyCommand): Request =
    replyTo => AccountKeyCommandWrapper(command, replyTo)

}

object AccountKeyHandler extends AccountKeyHandler

trait AccountKeyDao {_: AccountKeyHandler =>

  def lookupAccount(key: String)(implicit system: ActorSystem[_]): Option[String] = {
    this ? (generateUUID(Some(key)), LookupAccountKey) match {
      case r: AccountKeyFound =>
        import r._
        logger.info(s"found $account for $key")
        Some(account)
      case _                  =>
        logger.warn(s"could not find an account for $key")
        None
    }
  }

  def addAccountKey(key: String, account: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"adding ($key, $account)")
    this ! (generateUUID(Some(key)), AddAccountKey(account))
  }

  def removeAccountKey(key: String)(implicit system: ActorSystem[_]): Unit = {
    logger.info(s"removing ($key)")
    this ! (generateUUID(Some(key)), RemoveAccountKey)
  }

}

object AccountKeyDao extends AccountKeyDao with AccountKeyHandler
