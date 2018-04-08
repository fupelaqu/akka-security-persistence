package org.softnetwork.security.model

import org.scalatest.{Matchers, WordSpec}
import org.softnetwork.security.message.SignIn

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
      val maybeProfile = BaseAccount(signIn)
      maybeProfile.isDefined shouldBe true
      val profile = maybeProfile.get
      profile.username.isDefined shouldBe true
      profile.username.get shouldBe username
      Password.checkPassword(profile.credentials, password) shouldBe true
    }
    "work with email" in {
      val signIn = SignIn(email, password, password)
      val maybeProfile = BaseAccount(signIn)
      maybeProfile.isDefined shouldBe true
      val profile = maybeProfile.get
      profile.email.isDefined shouldBe true
      profile.email.get shouldBe email
      Password.checkPassword(profile.credentials, password) shouldBe true
    }
    "work with gsm" in {
      val signIn = SignIn(gsm, password, password)
      val maybeProfile = BaseAccount(signIn)
      maybeProfile.isDefined shouldBe true
      val profile = maybeProfile.get
      profile.gsm.isDefined shouldBe true
      profile.gsm.get shouldBe gsm
      Password.checkPassword(profile.credentials, password) shouldBe true
    }
  }

}
