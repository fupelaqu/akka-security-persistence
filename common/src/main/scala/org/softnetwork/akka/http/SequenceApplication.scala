package org.softnetwork.akka.http

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server._
import org.softnetwork.akka.persistence.typed.{EntityBehavior, Sequence}

/**
  * Created by smanciot on 15/05/2020.
  */
object SequenceApplication extends Application with PostgresGuardian {
  override lazy val systemName = "Softnetwork"

  override def routes: ActorSystem[_] => Route = system => new MainRoutes()(system).routes

  override def behaviors: ActorSystem[_] =>  Seq[EntityBehavior[_, _, _, _]] = _ => Seq(Sequence)

}
