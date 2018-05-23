package org.softnetwork.security

import org.json4s.ext.{EnumNameSerializer, JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.{NoTypeHints, jackson}
import org.json4s.jackson.Serialization
import org.softnetwork.akka.http.JavaTimeSerializers

/**
  * Created by smanciot on 16/05/2018.
  */
package object model {

  implicit val serialization = jackson.Serialization
  implicit val formats = Serialization.formats(NoTypeHints) ++
    JodaTimeSerializers.all ++
    JavaTypesSerializers.all ++
    JavaTimeSerializers.all ++
    Seq(new EnumNameSerializer(AccountStatus))

}
