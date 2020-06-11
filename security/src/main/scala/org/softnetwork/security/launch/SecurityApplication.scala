package org.softnetwork.security.launch

import akka.actor.typed.ActorSystem

import akka.http.scaladsl.server._

import org.softnetwork.akka.http.{Application, PostgresGuardian}
import org.softnetwork.akka.persistence.typed.EntityBehavior

import org.softnetwork.security.config.Settings

import org.softnetwork.security.handlers.BasicAccountDao

import org.softnetwork.security.persistence.typed.BasicAccountBehavior

import org.softnetwork.security.service.MainRoutes

import org.softnetwork.session.persistence.typed.SessionRefreshTokenBehavior

/**
  * Created by smanciot on 22/03/2018.
  */
object SecurityApplication extends Application with PostgresGuardian {

  override lazy val systemName = "Security"

  override def routes: ActorSystem[_] => Route = system => new MainRoutes()(system).routes

  override def behaviors: ActorSystem[_] =>  Seq[EntityBehavior[_, _, _, _]] = _ => Seq(
    BasicAccountBehavior,
    SessionRefreshTokenBehavior
  )

  override def initSystem(system: ActorSystem[_]) = {
    val root = Settings.AdministratorsConfig.root
    BasicAccountDao.initAdminAccount(root.login, root.password)(system)
  }
}
