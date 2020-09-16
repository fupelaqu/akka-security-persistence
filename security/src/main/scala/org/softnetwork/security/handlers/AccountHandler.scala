package org.softnetwork.security.handlers

import org.softnetwork.akka.handlers.EntityHandler
import org.softnetwork.akka.persistence.typed.CommandTypeKey
import org.softnetwork.security.message._
import org.softnetwork.security.persistence.typed.{MockBasicAccountBehavior, BasicAccountBehavior}

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

/**
  * Created by smanciot on 18/04/2020.
  */
trait AccountHandler extends EntityHandler[AccountCommand, AccountCommandResult] {_: CommandTypeKey[AccountCommand] =>

  private[this] val accountKeyDao = AccountKeyDao

  override protected def lookup[T](key: T): Option[String] = accountKeyDao.lookupAccount(key)

  override def ??[T](key: T, command: AccountCommand, atMost: FiniteDuration)(
    implicit tTag: ClassTag[AccountCommand]): AccountCommandResult =
    command match {
      case _: LookupAccountCommand => lookup(key) match {
        case Some(entityId) => this ? (entityId, command, atMost)
        case _ =>
          command match {
            case _: CheckResetPasswordToken => TokenNotFound
            case _: ResetPassword => CodeNotFound
            case _ => AccountNotFound
          }
      }
      case _ => this ? (key, command, atMost)
    }

}

trait AccountDao { _: AccountHandler =>

  import org.softnetwork._

  def initAdminAccount(login: String, password: String) = { // FIXME
    this ! (generateUUID(Some(login)), new InitAdminAccount(login, password)) /*match {
      case AdminAccountInitialized => true
      case _ => false
    }*/
  }
}

trait BasicAccountTypeKey extends CommandTypeKey[AccountCommand]{
  override def TypeKey(implicit tTag: ClassTag[AccountCommand]) = BasicAccountBehavior.TypeKey
}

trait MockBasicAccountTypeKey extends CommandTypeKey[AccountCommand]{
  override def TypeKey(implicit tTag: ClassTag[AccountCommand]) = MockBasicAccountBehavior.TypeKey
}

object BasicAccountDao extends AccountDao with AccountHandler with BasicAccountTypeKey

object MockBasicAccountHandler extends AccountHandler with MockBasicAccountTypeKey

