package org.softnetwork.session.message

import com.softwaremill.session.RefreshTokenData
import org.softnetwork.akka.message.Command

/**
  * Created by smanciot on 22/03/2018.
  */
sealed trait RefreshTokenCommand extends Command

case class LookupRefreshToken(selector: String) extends RefreshTokenCommand

case class StoreRefreshToken[T](data: RefreshTokenData[T]) extends RefreshTokenCommand

case class RemoveRefreshToken(selector: String) extends RefreshTokenCommand
