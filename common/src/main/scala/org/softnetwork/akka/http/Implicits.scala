package org.softnetwork.akka.http

import org.json4s._
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.jackson.Serialization

object Implicits {

  implicit val serialization = jackson.Serialization
  implicit val formats = Serialization.formats(NoTypeHints) ++
    JodaTimeSerializers.all ++
    JavaTypesSerializers.all ++
    JavaTimeSerializers.all

}
