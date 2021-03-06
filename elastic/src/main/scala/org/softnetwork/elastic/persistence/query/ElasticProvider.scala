package org.softnetwork.elastic.persistence.query

import com.typesafe.scalalogging.StrictLogging
import mustache.Mustache
import org.json4s.Formats
import org.softnetwork._
import org.softnetwork.akka.model.Timestamped
import org.softnetwork.akka.persistence.query.PersistenceProvider
import org.softnetwork.akka.serialization.commonFormats
import org.softnetwork.elastic.persistence.typed.Elastic._

import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 16/05/2020.
  */
trait ElasticProvider[T <: Timestamped] extends PersistenceProvider[T] with StrictLogging {_: ManifestWrapper[T] =>

  implicit def formats: Formats = commonFormats

  protected lazy val index = getIndex[T](manifestWrapper.wrapped)

  protected lazy val `type`: String = getType[T](manifestWrapper.wrapped)

  protected lazy val alias = getAlias[T](manifestWrapper.wrapped)

  protected def mappingPath: Option[String] = None

  protected def loadMapping(path: Option[String] = None): String = {
    val pathOrElse: String = path.getOrElse(s"""mapping/${`type`}.mustache""")
    Try(Mustache(pathOrElse).render(Map("type" -> `type`))) match {
      case Success(s) =>
        s
      case Failure(f) =>
        logger.error(s"$pathOrElse -> f.getMessage", f)
        "{}"
    }
  }

  /**
    * create the elastic index
    */
  protected def initIndex(): Unit

}
