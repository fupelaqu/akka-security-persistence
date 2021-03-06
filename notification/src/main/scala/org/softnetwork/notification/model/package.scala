package org.softnetwork.notification

import org.json4s.{NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}

import scala.language.implicitConversions
import scalapb.TypeMapper

/**
  * Created by smanciot on 24/04/2018.
  */
package object model {

  implicit val serialization = jackson.Serialization
  implicit val formats       = Serialization.formats(NoTypeHints) ++
    JodaTimeSerializers.all ++
    JavaTypesSerializers.all
}
