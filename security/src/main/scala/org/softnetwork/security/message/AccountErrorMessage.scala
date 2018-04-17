package org.softnetwork.security.message

import org.softnetwork.akka.message.ErrorMessage

/**
  * Created by smanciot on 19/03/2018.
  */
class AccountErrorMessage(override val message: String) extends ErrorMessage(message) with AccountCommandResult

case object LoginAlreadyExists extends AccountErrorMessage("LoginAlreadyExists")

case object LoginUnaccepted extends AccountErrorMessage("LoginUnaccepted")

case object AccountDisabled extends AccountErrorMessage("AccountDisabled")

case object PasswordsNotMatched extends AccountErrorMessage("PasswordsNotMatched")

case object LoginAndPasswordNotMatched extends AccountErrorMessage("LoginAndPasswordNotMatched")

case object UndeliveredActivationToken extends AccountErrorMessage("UndeliveredActivationToken")

case object TokenNotFound extends AccountErrorMessage("TokenNotFound")

case object TokenExpired extends AccountErrorMessage("TokenExpired")

case object AccountNotFound extends AccountErrorMessage("AccountNotFound")

case class InvalidPassword(errors: Seq[String] = Seq.empty) extends AccountErrorMessage(errors.mkString(","))

case object IllegalStateError extends AccountErrorMessage("IllegalStateError")

case object InvalidPrincipal extends AccountErrorMessage("InvalidPrincipal")

case object UndeliveredVerificationCode extends AccountErrorMessage("UndeliveredVerificationCode")

case object CodeNotFound extends AccountErrorMessage("TokenNotFound")

case object CodeExpired extends AccountErrorMessage("TokenNotFound")

case object DeviceRegistrationNotFound extends AccountErrorMessage("DeviceRegistrationNotFound")
