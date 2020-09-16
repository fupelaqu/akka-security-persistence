package org.softnetwork.akka.persistence.jdbc.util

import org.softnetwork.akka.persistence.jdbc.util.Schema.{SchemaType, Postgres => SPostgres}

/**
  * Created by smanciot on 14/05/2020.
  */
trait Postgres extends Db {
  override lazy val schemaType: SchemaType = SPostgres()
}
