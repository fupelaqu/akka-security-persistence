package org.softnetwork.security.service

import akka.actor.typed.ActorSystem

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, HttpResponse}

import org.softnetwork.akka.http.HealthCheckService

import org.softnetwork.akka.serialization._
import org.softnetwork.security.handlers.BasicAccountTypeKey

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 24/04/2020.
  */
class MainRoutes()(implicit val system: ActorSystem[_]) extends AccountService with BasicAccountTypeKey {

  def routes = {
    logRequestResult("RestAll") {
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
                entity = HttpEntity(
                  `application/json`,
                  serialization.write(
                    Map(
                      "message" -> f.getMessage
                    )
                  )
                )
              )
            )
        }
      }
    }
  }

  lazy val apiRoutes = HealthCheckService.route ~ route

}
