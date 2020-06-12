package org.softnetwork.akka

import akka.actor.typed.ActorRef
import org.json4s.Formats
import org.softnetwork.akka.model.Timestamped

/**
  * Created by smanciot on 19/03/2018.
  */
package object message {

  /** Command objects **/
  trait Command

  trait CommandWithReply[R <: CommandResult] extends Command {
    def replyTo: ActorRef[R]
  }

  trait CommandWrapper[C <: Command, R <: CommandResult] extends CommandWithReply[R] {
    def command: C
  }

  object CommandWrapper {
    def apply[C <: Command, R <: CommandResult](aCommand: C, aReplyTo: ActorRef[R]) = new CommandWrapper[C, R] {
      override val command = aCommand
      override val replyTo = aReplyTo
    }.asInstanceOf[C]
  }

  /** Entity command **/

  /**
    * when a command is not related to a specific entity
    */
  val ALL = "*"

  /**
    * a command that should be performed for a specific entity
    */
  trait EntityCommand extends Command {
    def id: String // TODO rename to uuid ?
  }

  /**
    * allow a command to be performed for no specific entity
    */
  trait AllEntities extends EntityCommand {_: Command =>
    override val id: String = ALL
  }

  /** Event objects **/
  trait Event

  /** Crud events **/
  trait CrudEvent extends Event

  trait Created[T <: Timestamped] extends CrudEvent {
    def document: T
  }

  trait Updated[T <: Timestamped] extends CrudEvent {
    def document: T
    def upsert: Boolean = true
  }

  trait Loaded[T <: Timestamped] extends CrudEvent {
    def document: T
  }

  trait Upserted extends CrudEvent {
    def uuid: String
    def data: String
  }

  trait UpsertedDecorator { _: Upserted =>
    import org.softnetwork.akka.serialization._
    implicit def formats: Formats = commonFormats
    override lazy val data = serialization.write(this)
  }

  trait Deleted extends CrudEvent {
    def uuid: String
  }

  /** Command result **/
  trait CommandResult

  @SerialVersionUID(0L)
  class ErrorMessage(val message: String) extends CommandResult

  case object UnknownCommand extends ErrorMessage("UnknownCommand")

  case object UnknownEvent extends ErrorMessage("UnknownEvent")

  /** Count command result **/
  case class CountResponse(field: String, count: Int, error: Option[String] = None)

  @SerialVersionUID(0L)
  abstract class CountResult(results: Seq[CountResponse]) extends CommandResult

  /** Protobuf events **/
  trait ProtobufEvent extends Event

  /** Cbor events **/
  trait CborEvent extends Event
}
