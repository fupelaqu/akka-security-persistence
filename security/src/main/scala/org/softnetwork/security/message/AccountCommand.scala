package org.softnetwork.security.message

import org.softnetwork.akka.message.Command

/**
  * Created by smanciot on 19/03/2018.
  */
sealed trait AccountCommand extends Command

case class SignIn(
  login: String,
  password: String,
  confirmPassword: String,
  firstName: Option[String] = None,
  lastName: Option[String] = None
) extends AccountCommand

case class SignOut(uuid: String) extends AccountCommand

case class Login(login: String, password: String, refreshable: Boolean = false) extends AccountCommand

case object Logout extends AccountCommand

case class UpdatePassword(login: String, oldPassword: String, newPassword: String, confirmedPassword: String) extends AccountCommand

case class Confirm(token: String) extends AccountCommand
