package org.softnetwork.security.model

import java.util.{Date, UUID}

import org.softnetwork.security.message.SignIn

/**
  * Created by smanciot on 03/04/2018.
  */
case class BaseAccount(
  override val uuid: String = UUID.randomUUID.toString,
  override val credentials: String, /*encrypted sha512*/
  override val lastLogin: Option[Date] = None,
  override val nbLoginFailures: Int = 0,
  override val status: AccountStatus.Value = AccountStatus.Inactive,
  firstName: Option[String] = None,
  lastName: Option[String] = None
)extends Account {
  override def copyWithCredentials(credentials: String): Account =
    copy(credentials = credentials)

  override def copyWithLastLogin(lastLogin: Option[Date]): Account =
    copy(lastLogin = lastLogin)

  override def copyWithNbLoginFailures(nbLoginFailures: Int): Account =
    copy(nbLoginFailures = nbLoginFailures)

  override def copyWithStatus(status: AccountStatus.Value): Account =
    copy(status = status)

  override def view = BaseAccountInfo(this)
}

/** Profile companion object **/
object BaseAccount{

  import Password._

  /** alias for email **/
  type Email = String

  /** alias for gsm **/
  type Gsm = String

  /** alias for username **/
  type Username = String

  val empty = new BaseAccount(credentials = "")

  def apply(command: SignIn): Option[BaseAccount] = {
    import command._
    if(!password.equals(confirmPassword))
      None
    else{
      Some(
        BaseAccount(
          credentials = sha512(password),
          firstName = firstName,
          lastName = lastName
        ).add(Principal(login.trim)).asInstanceOf[BaseAccount]
      )
    }
  }

}

class BaseAccountInfo(
  override val lastLogin: Option[Date] = None,
  override val status: AccountStatus.Value = AccountStatus.Inactive,
  val firstName: Option[String] = None,
  val lastName: Option[String] = None
) extends AccountInfo

object BaseAccountInfo{
  def apply(account: BaseAccount): BaseAccountInfo = new BaseAccountInfo(
    lastLogin = account.lastLogin,
    status    = account.status,
    firstName = account.firstName,
    lastName  = account.lastName
  )
}

