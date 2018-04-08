package org.softnetwork.akka

/**
  * Created by smanciot on 19/03/2018.
  */
package object message {

  /** Command objects **/
  trait Command

  /** Event objects **/
  trait Event

  /** Message objects **/
  trait CommandResult

  class ErrorMessage(val message: String) extends CommandResult

  case object UnknownCommand extends ErrorMessage("UnknownCommand")
}
