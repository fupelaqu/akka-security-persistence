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
  override val activationToken: Option[VerificationToken] = None,
  override val verificationCode: Option[VerificationCode] = None,
  override val createdDate: Date = new Date(),
  override val updatedDate: Date = new Date(),
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

  override def copyWithActivationToken(activationToken: Option[VerificationToken]): Account =
    copy(activationToken = activationToken)

  override def copyWithVerificationCode(verificationCode: Option[VerificationCode]): Account =
    copy(verificationCode = verificationCode)

  override def copyWithUpdatedDate(updatedDate: Date): Account =
    copy(updatedDate = updatedDate)

  override def view = BaseAccountInfo(this)

}

/** Profile companion object **/
object BaseAccount{

  import Sha512Encryption._

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
      val principal = Principal(login.trim)
      val status = if(principal.`type` == PrincipalType.Email)/* TODO Push notifications */ AccountStatus.Inactive else AccountStatus.Active
      Some(
        BaseAccount(
          credentials = encrypt(password),
          firstName = firstName,
          lastName = lastName,
          status = status
        ).add(principal).asInstanceOf[BaseAccount]
      )
    }
  }

}

class BaseAccountInfo(
  override val lastLogin: Option[Date] = None,
  override val status: String = AccountStatus.Inactive.toString,
  override val createdDate: Date = new Date(),
  override val updatedDate: Date = new Date(),
  val firstName: Option[String] = None,
  val lastName: Option[String] = None
) extends AccountInfo

object BaseAccountInfo{
  def apply(account: BaseAccount): BaseAccountInfo = new BaseAccountInfo(
    lastLogin = account.lastLogin,
    status    = account.status.toString, // name of AccountStatus.Value
    createdDate = account.createdDate,
    updatedDate = account.updatedDate,
    firstName = account.firstName,
    lastName  = account.lastName
  )
}

