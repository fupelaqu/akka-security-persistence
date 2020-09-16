package org.softnetwork.akka

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{ResponseEntity, HttpEntity}

import org.json4s.Formats

import org.softnetwork.akka.message.ErrorMessage

import scala.language.implicitConversions

/**
  * Created by smanciot on 03/06/2020.
  */
package object http {

  import org.softnetwork.akka.serialization._

  implicit def entityAsJson[T <: AnyRef: Manifest](entity: T)(implicit formats: Formats): ResponseEntity = {
    entity match {
      case error: ErrorMessage => HttpEntity(`application/json`, serialization.write(Map("message" -> error.message)))
      case _ => HttpEntity(`application/json`, serialization.write(entity))
    }
  }

}
