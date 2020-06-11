package org.softnetwork.elastic.persistence.typed

import org.softnetwork.akka.persistence.PersistenceTools

import org.softnetwork.akka.model.Timestamped

import scala.language.implicitConversions

import org.softnetwork._

/**
  * Created by smanciot on 10/04/2020.
  */
object Elastic {

  def index(`type`: String): String = {
    s"${`type`}s-${PersistenceTools.env}".toLowerCase
  }

  def alias(`type`: String): String = {
    s"${`type`}s-v${PersistenceTools.version}".toLowerCase
  }

  def getAlias[T <: Timestamped](implicit m: Manifest[T]): String = {
    alias(getType[T])
  }

  def getIndex[T <: Timestamped](implicit m: Manifest[T]): String = {
    index(getType[T])
  }

}
