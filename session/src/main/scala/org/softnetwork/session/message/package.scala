package org.softnetwork.session

import akka.actor.typed.ActorRef
import com.softwaremill.session.{RefreshTokenLookupResult, RefreshTokenData}
import org.softnetwork.akka.message.{Event, CommandResult, CommandWrapper, EntityCommand}

/**
  * Created by smanciot on 16/05/2020.
  */
package object message {

  /** Commands **/

  sealed trait RefreshTokenCommand extends EntityCommand {
    def selector: String
    override val id = selector
  }

  @SerialVersionUID(0L)
  case class RefreshTokenWrapper[C <: RefreshTokenCommand, R <: RefreshTokenResult](command: C, replyTo: ActorRef[R])
    extends CommandWrapper[C, R] with RefreshTokenCommand {
    override val selector = command.selector
  }

  @SerialVersionUID(0L)
  case class LookupRefreshToken(selector: String) extends RefreshTokenCommand

  @SerialVersionUID(0L)
  case class StoreRefreshToken[T](data: RefreshTokenData[T]) extends RefreshTokenCommand {
    override val selector = data.selector
  }

  @SerialVersionUID(0L)
  case class RemoveRefreshToken(selector: String) extends RefreshTokenCommand

  /** Commands Result **/

  sealed trait RefreshTokenResult extends CommandResult

  @SerialVersionUID(0L)
  case class LookupRefreshTokenResult[T](data: Option[RefreshTokenLookupResult[T]]) extends RefreshTokenResult

  sealed trait RefreshTokenEvent extends Event

  @SerialVersionUID(0L)
  case class RefreshTokenStored[T](data: RefreshTokenData[T]) extends RefreshTokenEvent with RefreshTokenResult

  @SerialVersionUID(0L)
  case class RefreshTokenRemoved(selector: String) extends RefreshTokenEvent with RefreshTokenResult}
