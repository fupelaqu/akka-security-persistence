package org.softnetwork.security.launch

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.softwaremill.macwire._
import org.softnetwork.akka.actors.ActorSystemLocator
import org.softnetwork.akka.http.config.Settings._
import org.softnetwork.security.service.MainRoutes

/**
  * Created by smanciot on 22/03/2018.
  */
class Application extends App {

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  ActorSystemLocator(system)

  val mainRoutes = wiredInModule(DependenciesInjection).lookupSingleOrThrow(classOf[MainRoutes])

  Http().bindAndHandle(mainRoutes.routes, Interface, Port)
}
