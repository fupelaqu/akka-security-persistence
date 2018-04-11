package org.softnetwork.security.model

import java.security.{MessageDigest, SecureRandom}
import java.util.{Calendar, Date, UUID}

import org.apache.commons.codec.digest.Sha2Crypt

/**
  * Created by smanciot on 25/03/2018.
  */
trait Account extends Principals {
  def uuid: String
  def credentials: String
  def lastLogin: Option[Date]
  def nbLoginFailures: Int
  def status: AccountStatus.Value

  final override val primaryPrincipal = Principal(PrincipalType.Uuid, uuid)
  def email: Option[String] = get(PrincipalType.Email).map(_.value)
  def gsm: Option[String] = get(PrincipalType.Gsm).map(_.value)
  def username: Option[String] = get(PrincipalType.Username).map(_.value)

  def activationToken: Option[VerificationToken]
  def verificationCode: Option[VerificationCode]

  def copyWithCredentials(credentials: String): Account
  def copyWithLastLogin(lastLogin: Option[Date]): Account
  def copyWithNbLoginFailures(nbLoginFailures: Int): Account
  def copyWithStatus(status: AccountStatus.Value): Account
  def copyWithActivationToken(activationToken: Option[VerificationToken]): Account
  def copyWithVerificationCode(verificationCode: Option[VerificationCode]): Account

  def view: AccountInfo
}

trait AccountInfo {
  def lastLogin: Option[Date]
  def status: AccountStatus.Value
}

object AccountStatus extends Enumeration {
  type AccountStatus = Value
  // user account should be disabled after a configurable number of successive login failures
  val Disabled = Value(-1, "Disabled")
  // user account has been deleted
  val Deleted = Value(0, "Deleted")
  // user account by default is inactive
  val Inactive = Value(1, "Inactive")
  // user account must be active in oprder to access platform
  val Active = Value(2, "Active")
}

object Hash {

  def md5Hash(text: String): String = MessageDigest.getInstance("MD5").digest(text.getBytes).map(0xFF & _).map { "%02x".format(_) }.foldLeft(""){_ + _}

}

sealed trait Encryption {
  def encrypt(clearText: String): String

  def checkEncryption(crypted: String, clearText: String): Boolean
}

object Sha512Encryption extends Encryption {

  def encrypt(clearText: String) = Sha2Crypt.sha512Crypt(clearText.getBytes("UTF-8").clone())

  def isEncrypted(crypted: String)  = crypted.startsWith("$6$")

  def checkEncryption(crypted: String, clearText: String) = {
    if(!isEncrypted(crypted))
      false
    else {
      val offset2ndDolar = crypted.indexOf('$', 1)
      if (offset2ndDolar < 0)
        false
      else {
        val offset3ndDolar = crypted.indexOf('$', offset2ndDolar + 1)
        if (offset3ndDolar < 0)
          false
        else {
          crypted.equals(Sha2Crypt.sha512Crypt(clearText.getBytes("UTF-8").clone(), crypted.substring(0, offset3ndDolar + 1)))
        }
      }
    }
  }
}

/**
  * A collection of all principals associated with a corresponding Subject.
  * A principal is just a security term for an identifying attribute, such as
  * a username or user id or social security number or anything else that can be considered an 'identifying' attribute
  * for a Subject
  *
  */
trait Principals{

  /**
    * variable to store all secondary principals
    */
  private[this] var _secondaryPrincipals: Seq[Principal] = Nil

  /**
    * primary principal
    */
  val primaryPrincipal = Principal(PrincipalType.Uuid, UUID.randomUUID.toString)

  /**
    *
    * @return all principals within this collection of principals
    */
  def principals: Seq[Principal] = Seq(primaryPrincipal) ++ secondaryPrincipals

  /**
    *
    * @return all secondary principals within this collection of principals
    */
  def secondaryPrincipals: Seq[Principal] = Seq() ++ _secondaryPrincipals

  /**
    *
    * @param principal - the principal to add to this collection of principals
    * @return the collection of principals
    */
  def add(principal: Principal) = {
    if(principal.`type` != PrincipalType.Uuid){
      _secondaryPrincipals = _secondaryPrincipals.filterNot(_.`type` == principal.`type`)  :+ principal
    }
    this
  }

  def addAll(principals: Seq[Principal]): Principals = {
    principals match {
      case Nil        => this
      case head::tail =>
        add(head)
        addAll(tail)
    }
  }

  /**
    *
    * @param `type` - type of Principal to look for within this collection of principals
    * @return Some[Principal] if a principal of this type has been found, None otherwise
    */
  def get(`type`: PrincipalType.Value): Option[Principal] = principals.find(_.`type` == `type`)
}

case class Principal(`type`: PrincipalType.Value, value: String)

object Principal{
  def apply(principal: String): Principal = {
    if(EmailValidator.check(principal)){
      Principal(PrincipalType.Email, principal)
    }
    else if(GsmValidator.check(principal)){
      Principal(PrincipalType.Gsm, principal)
    }
    else{
      Principal(PrincipalType.Username, principal)
    }
  }
}

object PrincipalType extends Enumeration {
  type PrincipalType = Value
  val Other = Value(-1, "Other")
  val Uuid = Value(0, "Uuid")
  val Email = Value(1, "Email")
  val Gsm = Value(2, "Gsm")
  val Username = Value(3, "Username")
}

case class VerificationToken(token: String, expirationDate: Long)

trait ExpirationDate {

  def compute(expiryTimeInMinutes: Int) = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, expiryTimeInMinutes)
    cal.getTime
  }

}

object VerificationToken extends ExpirationDate {

  def apply(login: String, expiryTimeInMinutes: Int): VerificationToken = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, expiryTimeInMinutes)
    VerificationToken(BearerTokenGenerator.generateSHAToken(login), compute(expiryTimeInMinutes).getTime)
  }

}

case class VerificationCode(code: String, expirationDate: Long)

object VerificationCode extends ExpirationDate {

  def apply(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    VerificationCode(
      s"%0${pinSize}d".format(new SecureRandom().nextInt(math.pow(10, pinSize).toInt)),
      compute(expiryTimeInMinutes).getTime
    )
  }

}

