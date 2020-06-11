package org.softnetwork.akka.message

import akka.actor.typed.ActorRef

/**
  * Created by smanciot on 19/05/2020.
  */
object SequenceMessages {
  sealed trait SequenceCommand extends EntityCommand {
    def sequence: String
    override val id = sequence
  }

  @SerialVersionUID(0L)
  case class SequenceCommandWrapper(command: SequenceCommand, replyTo: ActorRef[SequenceResult]) extends
    CommandWrapper[SequenceCommand, SequenceResult] with SequenceCommand{
    override val sequence = command.sequence
  }

  @SerialVersionUID(0L)
  case class IncSequence(sequence: String) extends SequenceCommand

  @SerialVersionUID(0L)
  case class DecSequence(sequence: String) extends SequenceCommand

  @SerialVersionUID(0L)
  case class ResetSequence(sequence: String) extends SequenceCommand

  @SerialVersionUID(0L)
  case class LoadSequence(sequence: String) extends SequenceCommand

  trait SequenceResult extends CommandResult

  case object SequenceNotFound extends SequenceResult
}
