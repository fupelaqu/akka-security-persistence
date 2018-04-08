package org.softnetwork.session.message

import com.softwaremill.session.{RefreshTokenData, RefreshTokenLookupResult}
import org.softnetwork.akka.message.{Event, CommandResult}

/**
  * Created by smanciot on 22/03/2018.
  */
sealed trait RefreshTokenResult extends CommandResult

case class LookupRefreshTokenResult[T](data: Option[RefreshTokenLookupResult[T]]) extends RefreshTokenResult

case class RefreshTokenStored[T](data: RefreshTokenData[T]) extends Event with RefreshTokenResult

case class RefreshTokenRemoved(selector: String) extends Event with RefreshTokenResult