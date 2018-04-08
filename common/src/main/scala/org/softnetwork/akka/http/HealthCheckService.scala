package org.softnetwork.akka.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives


/**
  * Simplest possible implementation of a health check
  * More realistic implementation should include actual checking of the service's internal state,
  * verifying needed actors are still alive, and so on.
  */
class HealthCheckService extends Directives with DefaultComplete {

  val route = {
    path("healthcheck") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }
}
