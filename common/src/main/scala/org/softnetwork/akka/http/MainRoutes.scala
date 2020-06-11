package org.softnetwork.akka.http

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}

import scala.util.{Failure, Success, Try}

import org.softnetwork.akka.serialization._

/**
  * Created by smanciot on 24/04/2020.
  */
class MainRoutes()(implicit val system: ActorSystem[_]) extends SequenceService {

  def routes = {
    pathPrefix("api"){
      Try(
        apiRoutes
      ) match {
        case Success(s) => s
        case Failure(f) =>
          logger.error(f.getMessage, f.getCause)
          complete(
            HttpResponse(
              StatusCodes.InternalServerError,
              entity = Map("message" -> f.getMessage)
            )
          )
      }
    }
  }

  lazy val apiRoutes = HealthCheckService.route ~ route

}
