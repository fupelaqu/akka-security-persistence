package org.softnetwork.akka.persistence

import org.softnetwork.build.info.common.BuildInfo

/**
  * Created by smanciot on 14/02/2020.
  */
object PersistenceTools {

  /**
    * Used for akka and elastic persistence ids, one per targeted environment (development, production, ...)
    */
  val env = sys.env.getOrElse(
    "TARGETED_ENV",
    if(BuildInfo.version.endsWith("FINAL")){
      "prod"
    }
    else{
      "dev"
    }
  )

  val version = sys.env.getOrElse("VERSION", BuildInfo.version)

}
