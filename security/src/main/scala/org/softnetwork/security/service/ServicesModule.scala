package org.softnetwork.security.service

import com.softwaremill.macwire._
import org.softnetwork.akka.http.HealthCheckService
import org.softnetwork.security.handlers.HandlersModule

/**
  * Created by smanciot on 20/03/2018.
  */
trait ServicesModule extends HandlersModule {

  lazy val healthcheckService: HealthCheckService = wire[HealthCheckService]

  lazy val accountService: AccountService = wire[AccountService]
}
