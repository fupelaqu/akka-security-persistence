package org.softnetwork.akka.http

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, HttpResponse}
import akka.http.scaladsl.server.Directives
import org.softnetwork.akka.handlers.SequenceHandler

import scala.concurrent.duration.FiniteDuration

import org.softnetwork.akka.message._
import org.softnetwork.akka.serialization._

import SequenceMessages._

/**
  * Created by smanciot on 15/05/2020.
  */
trait SequenceService extends EntityService[SequenceCommand, SequenceResult]
  with SequenceHandler
  with Directives
  with DefaultComplete {

  implicit def atMost: FiniteDuration = defaultAtMost

  implicit def formats = commonFormats

  val route = {
    pathPrefix("sequence"){
      sequence
    }
  }

  lazy val sequence = pathPrefix(Segment){sequence =>
    pathEnd {
      post {
        run(sequence, IncSequence(sequence)) match {
          case r: SequenceIncremented => complete(
            HttpResponse(
              StatusCodes.OK,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map(r.name -> r.value)
                )
              )
            )
          )
          case other => complete(
            HttpResponse(
              StatusCodes.InternalServerError,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map("error" -> other.getClass)
                )
              )
            )
          )
        }
      } ~
      get {
        run(sequence, LoadSequence(sequence)) match {
          case r: SequenceLoaded => complete(
            HttpResponse(
              StatusCodes.OK,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map(r.name -> r.value)
                )
              )
            )
          )
          case other => complete(
            HttpResponse(
              StatusCodes.InternalServerError,
              entity = HttpEntity(
                `application/json`,
                serialization.write(
                  Map("error" -> other.getClass)
                )
              )
            )
          )
        }
      }
    }
  }

}
