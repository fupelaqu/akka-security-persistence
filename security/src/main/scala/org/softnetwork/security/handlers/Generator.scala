package org.softnetwork.security.handlers

import org.softnetwork.security.model.{ExpirationDate, VerificationCode, VerificationToken}

/**
  * Created by smanciot on 09/04/2018.
  */
trait Generator {
  val oneDay = 24*60
  def generateToken(login: String, expiryTimeInMinutes:Int = oneDay): VerificationToken
  def generatePinCode(pinSize: Int, expiryTimeInMinutes:Int = 5): VerificationCode
}

trait DefaultGenerator extends Generator {

  override def generateToken(login: String, expiryTimeInMinutes:Int): VerificationToken =
    VerificationToken(login, expiryTimeInMinutes)

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode =
    VerificationCode(pinSize, expiryTimeInMinutes)
}

trait MockGenerator extends Generator with ExpirationDate {
  val token = "token"

  val code  = "code"

  override def generateToken(login: String, expiryTimeInMinutes:Int): VerificationToken = {
    VerificationToken(token, compute(expiryTimeInMinutes).getTime)
  }

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    VerificationCode(code, compute(expiryTimeInMinutes).getTime)
  }

}