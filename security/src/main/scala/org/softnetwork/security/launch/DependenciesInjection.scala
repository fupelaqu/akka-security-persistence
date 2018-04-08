package org.softnetwork.security.launch

import com.softwaremill.macwire._
import org.softnetwork.security.handlers.HandlersModule
import org.softnetwork.security.service.{MainRoutes, ServicesModule}

/**
  * Created by smanciot on 22/03/2018.
  */
object DependenciesInjection extends HandlersModule with ServicesModule {

  lazy val routes: MainRoutes = wire[MainRoutes]

}
