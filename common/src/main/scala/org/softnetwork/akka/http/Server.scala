package org.softnetwork.akka.http

import akka.Done

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem

import akka.http.scaladsl.Http
import akka.http.scaladsl.server._

import org.softnetwork.akka.http.config.Settings._

import scala.util.{Failure, Success}

import scala.concurrent.duration._

import akka.{actor => classic}

/**
  * Created by smanciot on 25/04/2020.
  */
trait Server {
  def routes: Route

  implicit def classicSystem: classic.ActorSystem

  private lazy val shutdown = CoordinatedShutdown(classicSystem)

  implicit lazy val ec = classicSystem.dispatcher

  def start(): Unit = {
    Http().bindAndHandle(routes, Interface, Port).onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        classicSystem.log.info(
          s"${classicSystem.name} application started at http://{}:{}/",
          address.getHostString,
          address.getPort
        )

        shutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "http-graceful-terminate") { () =>
          binding.terminate(10.seconds).map { _ =>
            classicSystem.log.info(
              s"${classicSystem.name} application http://{}:{}/ graceful shutdown completed",
              address.getHostString,
              address.getPort
            )
            Done
          }
        }
      case Failure(ex) =>
        classicSystem.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        classicSystem.terminate()
    }
  }
}

object Server{
  def apply(r: Route, s: ActorSystem[_]): Server = {
    new Server(){
      override val routes = r
      import org.softnetwork.akka.persistence.typed._
      override implicit lazy val classicSystem: classic.ActorSystem = s
    }
  }
}