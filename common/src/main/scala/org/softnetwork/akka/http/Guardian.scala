package org.softnetwork.akka.http

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import akka.cluster.typed.{Cluster, Join}

import akka.http.scaladsl.server.Route

import akka.{actor => classic}

import org.softnetwork.akka.persistence.PersistenceTools

import org.softnetwork.akka.persistence.jdbc.util.{Postgres, Db}

import org.softnetwork.akka.persistence.query.{EventProcessor, EventProcessorStream}

import org.softnetwork.akka.persistence.typed.{EntitySystemLocator, EntityBehavior}

/**
  * Created by smanciot on 15/05/2020.
  */
trait Guardian {_: Db =>

  /**
    *
    * initialize all application routes
    *
    */
  def routes: ActorSystem[_] => Route

  /**
    * initialize all behaviors
    *
    */
  def behaviors: ActorSystem[_] => Seq[EntityBehavior[_, _, _, _]] = _ => Seq.empty

  /**
    * initialize all event processor streams
    *
    */
  def eventProcessorStreams: ActorSystem[_] => Seq[EventProcessorStream[_]] = _ => Seq.empty

  /**
    *
    * @return whether or not this node should join the cluster
    */
  def joinCluster(): Boolean = true

  def initSystem(system: ActorSystem[_]) = {}

  def banner: String =
    """
      | ____         __ _              _                      _
      |/ ___|  ___  / _| |_ _ __   ___| |___      _____  _ __| | __
      |\___ \ / _ \| |_| __| '_ \ / _ \ __\ \ /\ / / _ \| '__| |/ /
      | ___) | (_) |  _| |_| | | |  __/ |_ \ V  V / (_) | |  |   <
      ||____/ \___/|_|  \__|_| |_|\___|\__| \_/\_/ \___/|_|  |_|\_\
      |
      |""".stripMargin

  def init(selfJoin: Boolean = true): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      // initialize database
      initSchema()

      val system = context.system

      EntitySystemLocator(system)

      // initialize behaviors
      for(behavior <- behaviors(system)) {
        behavior.init(system)
      }

      // join the cluster
      if(joinCluster()){
        // join cluster
        val address: classic.Address = Cluster(system).selfMember.address
        system.log.info(s"Try to join this cluster node with the node specified by [$address]")
        Cluster(system).manager ! Join(address)
      }

      // initialize event streams
      for(eventProcessorStream <- eventProcessorStreams(system)) {
        context.spawnAnonymous[Nothing](EventProcessor(eventProcessorStream))
      }

      // print a cool banner ;)
      println(banner)
      println(s"V ${PersistenceTools.version}")

      // additional system initialization
      initSystem(system)

      // start the server
      Server(routes(system), system).start()

      Behaviors.empty
    }
  }
}

trait PostgresGuardian extends Guardian with Postgres
