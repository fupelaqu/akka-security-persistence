package org.softnetwork.security.service

import akka.http.scaladsl.server.Directives
import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.akka.http.HealthCheckService

/**
  * Created by smanciot on 22/03/2018.
  */
class MainRoutes(healthCheckService: HealthCheckService, accountService: AccountService) extends Directives
  with StrictLogging{

  def routes = {
    logRequestResult("RestAll") {
        pathPrefix("api"){
          apiRoutes
        }
    }
  }

  lazy val apiRoutes = healthCheckService.route ~ accountService.route

}
