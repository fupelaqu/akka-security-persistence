package org.softnetwork.security.config

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{Matchers, WordSpec}
import Password._

import Settings._

/**
  * Created by smanciot on 12/04/2018.
  */
class SettingsSpec extends WordSpec with Matchers with StrictLogging {

  val lengthRule = ConfigFactory.parseString("""security {
                                            |  password {
                                            |    length {
                                            |      min = 8
                                            |      max = 16
                                            |    }
                                            |  }
                                            |}""".stripMargin)

  val upperCaseCharacterRule = ConfigFactory.parseString("""security {
                                               |  password {
                                               |    upperCaseCharacter {
                                               |      size = 2
                                               |    }
                                               |  }
                                               |}""".stripMargin)

  val lowerCaseCharacterRule = ConfigFactory.parseString("""security {
                                                           |  password {
                                                           |    lowerCaseCharacter {
                                                           |      size = 2
                                                           |    }
                                                           |  }
                                                           |}""".stripMargin)

  val numberCharacterRule = ConfigFactory.parseString("""security {
                                                           |  password {
                                                           |    numberCharacter {
                                                           |      size = 2
                                                           |    }
                                                           |  }
                                                           |}""".stripMargin)

  val allowedRegexRule = ConfigFactory.parseString("""security {
                                                        |  password {
                                                        |    allowedRegex {
                                                        |      regex = "\\w*[^A-Za-z0-9 ]\\w*"
                                                        |    }
                                                        |  }
                                                        |}""".stripMargin)

  val allowedCharacterRule = ConfigFactory.parseString("""security {
                                                     |  password {
                                                     |    allowedCharacter {
                                                     |      chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                                                     |    }
                                                     |  }
                                                     |}""".stripMargin)

  val whitespaceRule = ConfigFactory.parseString("""security {
                                                         |  password {
                                                         |    whitespace {
                                                         |    }
                                                         |  }
                                                         |}""".stripMargin)

  val specialCharacterRule = ConfigFactory.parseString("""security {
                                                        |  password {
                                                        |    specialCharacter {
                                                        |      size = 2
                                                        |    }
                                                        |  }
                                                        |}""".stripMargin)

  "Settings" must {
    "accept password length rule" in {
      val rules = passwordRules(lengthRule)
      rules.length match {
        case Some(Length(min, max)) if min==8 && max==16 =>
        case _                                           => fail()
      }
      rules.validate("abcdefgh").isRight shouldBe true
      rules.validate("abcdefghabcdefgh").isRight shouldBe true
      rules.validate("abcdefg").isLeft shouldBe true
      rules.validate("abcdefghabcdefgha").isLeft shouldBe true
    }
    "accept password upperCaseCharacter rule" in {
      val rules = passwordRules(upperCaseCharacterRule)
      rules.upperCaseCharacter match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("aAbB").isRight shouldBe true
      rules.validate("aAbb").isLeft shouldBe true
    }
    "accept password lowerCaseCharacter rule" in {
      val rules = passwordRules(lowerCaseCharacterRule)
      rules.lowerCaseCharacter match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("abAB").isRight shouldBe true
      rules.validate("ABaB").isLeft shouldBe true
    }
    "accept password numberCharacter rule" in {
      val rules = passwordRules(numberCharacterRule)
      rules.numberCharacter match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("ab1A2B").isRight shouldBe true
      rules.validate("ab1AB").isLeft shouldBe true
    }
    "accept password allowedRegex rule" in {
      val rules = passwordRules(allowedRegexRule)
      rules.allowedRegex match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("ab@A2B").isRight shouldBe true
      rules.validate("abA2B").isLeft shouldBe true
    }
    "accept password allowedCharacter rule" in {
      val rules = passwordRules(allowedCharacterRule)
      rules.allowedCharacter match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("abcdAB123").isRight shouldBe true
      rules.validate("abcdAB_123").isLeft shouldBe true
    }
    "accept password whitespace rule" in {
      val rules = passwordRules(whitespaceRule)
      rules.whitespace match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("abcdAB123").isRight shouldBe true
      rules.validate("abcdAB 123").isLeft shouldBe true
    }
    "accept password specialCharacter rule" in {
      val rules = passwordRules(specialCharacterRule)
      rules.specialCharacter match {
        case Some(_) =>
        case _       => fail()
      }
      rules.validate("abcd@_AB123").isRight shouldBe true
      rules.validate("abcd_AB123").isLeft shouldBe true
    }
  }
}
