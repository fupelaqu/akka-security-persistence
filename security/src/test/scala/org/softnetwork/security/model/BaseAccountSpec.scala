package org.softnetwork.security.model

import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.security.message.SignIn
import Sha512Encryption._

/**
  * Created by smanciot on 18/03/2018.
  */
class BaseAccountSpec extends WordSpec with Matchers {

  private val username = "smanciot"
  private val email = "stephane.manciot@gmail.com"
  private val gsm = "0660010203"
  private val password = "changeit"

  "BaseAccount creation" should {
    "work with username" in {
      val signIn = SignIn(username, password, password)
      val maybeBaseAccount = BaseAccount(signIn)
      maybeBaseAccount.isDefined shouldBe true
      val baseAccount = maybeBaseAccount.get
      baseAccount.username.isDefined shouldBe true
      baseAccount.username.get shouldBe username
      checkEncryption(baseAccount.credentials, password) shouldBe true
    }
    "work with email" in {
      val signIn = SignIn(email, password, password)
      val maybeBaseAccount = BaseAccount(signIn)
      maybeBaseAccount.isDefined shouldBe true
      val baseAccount = maybeBaseAccount.get
      baseAccount.email.isDefined shouldBe true
      baseAccount.email.get shouldBe email
      checkEncryption(baseAccount.credentials, password) shouldBe true
    }
    "work with gsm" in {
      val signIn = SignIn(gsm, password, password)
      val maybeBaseAccount = BaseAccount(signIn)
      maybeBaseAccount.isDefined shouldBe true
      val baseAccount = maybeBaseAccount.get
      baseAccount.gsm.isDefined shouldBe true
      baseAccount.gsm.get shouldBe gsm
      checkEncryption(baseAccount.credentials, password) shouldBe true
    }
  }

}
