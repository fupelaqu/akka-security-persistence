package org.softnetwork.security.model

import org.softnetwork._
import org.softnetwork.security.config.Settings
import org.softnetwork.security.message.{InitAdminAccount, SignUp}

/**
  * Created by smanciot on 03/04/2018.
  */
trait BasicAccountCompanion{

  import Sha512Encryption._

  /** alias for email **/
  type Email = String

  /** alias for gsm **/
  type Gsm = String

  /** alias for username **/
  type Username = String

  def apply(command: SignUp): Option[BasicAccount] = {
    apply(command, None)
  }

  def apply(command: SignUp, uuid: Option[String]): Option[BasicAccount] = {
    import command._
    val _confirmPassword = confirmPassword match {
      case Some(p) => p
      case _       => password
    }
    if(!password.equals(_confirmPassword))
      None
    else{
      val principal = Principal(login.trim)
      val admin = command match {
        case _: InitAdminAccount => true
        case _ => false
      }
      val status =
        if(!admin && Settings.ActivationEnabled && principal.`type` == PrincipalType.Email)/* TODO Push notifications */
          AccountStatus.Inactive
        else
          AccountStatus.Active
      import org.softnetwork._
      val account =
        BasicAccount.defaultInstance
          .withUuid(uuid.getOrElse(generateUUID()))
          .withCreatedDate(now())
          .withLastUpdated(now())
          .withPrincipal(principal)
          .withCredentials(encrypt(password))
          .withStatus(status)
          .add(principal)
      if(admin){
        Some(
          account.add(
            BasicAccountProfile.defaultInstance.withName("admin")
              .withType(ProfileType.ADMINISTRATOR)
              .withFirstName(firstName.getOrElse("admin"))
              .withLastName(lastName.getOrElse(""))
          ).asInstanceOf[BasicAccount]
        )
      }
      else{
        Some(
          account.asInstanceOf[BasicAccount]
        )
      }
    }
  }

}

trait BasicAccountDecorator {_: BasicAccount =>
  override def newProfile(name: String): Profile = BasicAccountProfile.defaultInstance.withName(name)
}