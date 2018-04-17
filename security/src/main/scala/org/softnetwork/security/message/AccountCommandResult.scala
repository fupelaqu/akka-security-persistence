package org.softnetwork.security.message

import org.softnetwork.akka.message.{Event, CommandResult}
import org.softnetwork.security.actors.{DeviceRegistrationWithUuid, ActivationTokenWithUuid, VerificationCodeWithUuid}
import org.softnetwork.security.model.Account

/**
  * Created by smanciot on 19/03/2018.
  */
trait AccountCommandResult extends CommandResult

case class AccountCreated[T <: Account](account: T) extends Event with AccountCommandResult

case class AccountUpdated[T <: Account](account: T) extends Event with AccountCommandResult

case class ActivationTokenEvent(activationToken: ActivationTokenWithUuid) extends Event

case class RemoveActivationTokenEvent(activationToken: String) extends Event

class AccountActivated[T <: Account](override val account: T) extends AccountUpdated[T](account)

class AccountDeleted[T <: Account](override val account: T) extends  AccountUpdated[T](account)

class PasswordUpdated[T <: Account](override val account: T) extends AccountUpdated[T](account)

class LoginSucceeded[T <: Account](override val account: T) extends AccountUpdated[T](account)

class LoginFailed[T <: Account](override val account: T) extends AccountUpdated[T](account)

case object LogoutSucceeded extends AccountCommandResult

case class VerificationCodeEvent(verificationCode: VerificationCodeWithUuid) extends Event

case object VerificationCodeSent extends AccountCommandResult

case class RemoveVerificationCodeEvent(verificationCode: String) extends Event

case object PasswordReseted extends AccountCommandResult

case object DeviceRegistered extends AccountCommandResult

case object DeviceUnregistered extends AccountCommandResult

case class RegisterDeviceEvent(registration: DeviceRegistrationWithUuid) extends Event

case class UnregisterDeviceEvent(registration: DeviceRegistrationWithUuid) extends Event
