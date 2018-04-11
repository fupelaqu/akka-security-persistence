package org.softnetwork.security.handlers

import java.util.Calendar

import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.security.model.{VerificationCode, VerificationToken}

/**
  * Created by smanciot on 09/04/2018.
  */
trait Generator {
  val oneDay = 24*60
  def generateToken(login: String, expiryTimeInMinutes:Int = oneDay): VerificationToken
  def generatePinCode(pinSize: Int, expiryTimeInMinutes:Int = 5): VerificationCode
}

class DefaultGenerator extends Generator {

  override def generateToken(login: String, expiryTimeInMinutes:Int): VerificationToken =
    VerificationToken(login, expiryTimeInMinutes)

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode =
    VerificationCode(pinSize, expiryTimeInMinutes)
}

class MockGenerator extends Generator {
  val token = "token"

  val code  = "code"

  override def generateToken(login: String, expiryTimeInMinutes:Int): VerificationToken = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, expiryTimeInMinutes)
    VerificationToken(token, cal.getTime.getTime)
  }

  override def generatePinCode(pinSize: Int, expiryTimeInMinutes: Int): VerificationCode = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, expiryTimeInMinutes)
    VerificationCode(code, cal.getTime.getTime)
  }

}