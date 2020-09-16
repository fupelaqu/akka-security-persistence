package org.softnetwork.akka.http

import akka.actor.typed.ActorSystem

/**
  * Created by smanciot on 22/03/2018.
  */
trait Application extends App {_: Guardian =>

  def systemName: String

  ActorSystem[Nothing](init(), systemName)

}
