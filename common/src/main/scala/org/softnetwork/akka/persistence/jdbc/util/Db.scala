/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softnetwork.akka.persistence.jdbc.util

import java.sql.Statement

import akka.persistence.jdbc.config.SlickConfiguration
import akka.persistence.jdbc.util.SlickDatabase
import com.typesafe.config.ConfigFactory
import org.softnetwork.akka.persistence.jdbc.util.Schema.SchemaType
import slick.jdbc.JdbcBackend.{ Database, Session }


object Schema {

  sealed trait SchemaType { def schema: String }
  final case class Postgres(schema: String = "schema/postgres-schema.sql") extends SchemaType
  final case class H2(schema: String = "schema/h2-schema.sql") extends SchemaType
  final case class MySQL(schema: String = "schema/mysql-schema.sql") extends SchemaType
}

trait Db extends ClasspathResources {

  def schemaType: SchemaType

  def cfg = ConfigFactory.load()

  def db: Database = SlickDatabase.forConfig(cfg, new SlickConfiguration(cfg.getConfig("slick")))

  def initSchema(separator: String = ";"): Unit = create(schemaType.schema, separator)

  def withDatabase[A](f: Database => A): A = f(db)

  def withSession[A](f: Session => A): A = {
    withDatabase { db =>
      val session = db.createSession()
      try f(session) finally session.close()
    }
  }

  def withStatement[A](f: Statement => A): A = withSession(session => session.withStatement()(f))

  private[this] def create(schema: String, separator: String = ";"): Unit = for {
    schema <- Option(fromClasspathAsString(schema))
    ddl <- for {
      trimmedLine <- schema.split(separator) map (_.trim)
      if trimmedLine.nonEmpty
    } yield trimmedLine
  } withStatement { stmt =>
    try stmt.executeUpdate(ddl) catch {
      case t: java.sql.SQLSyntaxErrorException if t.getMessage contains "ORA-00942" => // suppress known error message in the test
      case other: Throwable => System.err.println(other.getMessage)
    }
  }

}