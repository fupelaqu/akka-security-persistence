package org.softnetwork.security.message

import org.softnetwork.akka.message.{Event, CommandResult}
import org.softnetwork.security.model.Account

/**
  * Created by smanciot on 19/03/2018.
  */
trait AccountCommandResult extends CommandResult

case class AccountCreated[T <: Account](account: T) extends Event with AccountCommandResult

case class AccountUpdated[T <: Account](account: T) extends Event with AccountCommandResult

case class AccountConfirmed[T <: Account](account: T) extends Event with AccountCommandResult

class AccountDeleted[T <: Account](override val account: T) extends  AccountUpdated[T](account)

class PasswordUpdated[T <: Account](override val account: T) extends AccountUpdated[T](account)

class LoginSucceeded[T <: Account](override val account: T) extends AccountUpdated[T](account)

class LoginFailed[T <: Account](override val account: T) extends AccountUpdated[T](account)

case object LogoutSucceeded extends AccountCommandResult