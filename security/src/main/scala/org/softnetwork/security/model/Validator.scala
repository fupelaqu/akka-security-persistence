package org.softnetwork.security.model

import scala.util.matching.Regex

/**
  * Created by smanciot on 25/03/2018.
  */
/** trait used for validation **/
trait Validator[T] {
  def check(value: T): Boolean
}

/** Validator using regex */
trait RegexValidator extends Validator[String]{
  def regex: Regex
  def check(value: String): Boolean = value match{
    case null => false
    case _    => !value.trim.isEmpty && regex.unapplySeq(value).isDefined
  }
}

case class ValidationError(msg: String)

/** validator for email **/
object EmailValidator extends RegexValidator {
  val regex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r
}

/** validator for gsm **/
object GsmValidator extends RegexValidator {
  val regex = "^(\\+[1-9]{1}[0-9]{3,14})|([0-9]{10})$".r
}

