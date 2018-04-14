package org.softnetwork.security.model

import java.security.SecureRandom
import java.util.Calendar

/**
  * Created by smanciot on 14/04/2018.
  */
trait ExpirationDate {

  def compute(expiryTimeInMinutes: Int) = {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, expiryTimeInMinutes)
    cal.getTime
  }

}

case class VerificationToken(token: String, expirationDate: Long)

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

