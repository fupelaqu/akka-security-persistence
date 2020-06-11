package org.softnetwork.elastic.client

import java.util
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import io.searchbox.action.Action
import io.searchbox.client.{JestClient, JestResult, JestResultHandler}
import io.searchbox.core._
import org.json4s.Formats
import org.softnetwork.ManifestWrapper
import org.softnetwork.akka.model.Timestamped

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by smanciot on 12/04/2020.
  */
trait MockElasticApi[T <: Timestamped] extends ElasticApi[T] {_: ManifestWrapper[T] =>

  protected val elasticDocuments: ElasticDocuments[T] = new ElasticDocuments[T](){}

  override def apply(esCredentials: ElasticCredentials,
                          multithreaded: Boolean,
                          timeout: Int,
                          discoveryEnabled: Boolean,
                          discoveryFrequency: Long,
                          discoveryFrequencyTimeUnit: TimeUnit): JestClient = {
    new JestClient {
      override def shutdownClient(): Unit = {}

      override def executeAsync[J <: JestResult](clientRequest: Action[J], jestResultHandler: JestResultHandler[_ >: J]): Unit =
        throw new UnsupportedOperationException

      override def execute[J <: JestResult](clientRequest: Action[J]): J =
        throw new UnsupportedOperationException

      override def setServers(servers: util.Set[String]): Unit = {}

      override def close(): Unit = {}

    }
  }

  override def toggleRefresh(index: String, enable: Boolean): Unit = {}

  override def setReplicas(index: String, replicas: Int): Unit = {}

  override def addAlias(index: String, alias: String): Boolean = true

  override def createIndex(index: String): Boolean = true

  override def setMapping(index: String, `type`: String, mapping: String): Boolean = true

  override def deleteIndex(index: String): Boolean = true

  override def prepareBulk(index: String): Unit = {}

  /**
    * +----------+
    * |          |
    * |  Source  |  items: Iterator[D]
    * |          |
    * +----------+
    * |
    * v
    * +----------+
    * |          |
    * |transform | BulkableAction
    * |          |
    * +----------+
    * |
    * v
    * +----------+
    * |          |
    * | settings | Update elasticsearch settings (refresh and replicas)
    * |          |
    * +----------+
    * |
    * v
    * +----------+
    * |          |
    * |  group   |
    * |          |
    * +----------+
    * |
    * v
    * +----------+        +----------+
    * |          |------->|          |
    * |  balance |        |   bulk   |
    * |          |------->|          |
    * +----------+        +----------+
    * |    |
    * |    |
    * |    |
    * +---------+            |    |
    * |         |<-----------'    |
    * |  merge  |                 |
    * |         |<----------------'
    * +---------+
    * |
    * v
    * +----------+
    * |          |
    * | result   | BulkResult
    * |          |
    * +----------+
    * |
    * v
    * +----------+
    * |          |
    * |   Sink   | indices: Set[String]
    * |          |
    * +----------+
    *
    * Asynchronously bulk items in Elasticsearch
    *
    * @param items         the items to index
    * @param toDocument    to transform documents to json strings
    * @param idKey         the key where to find the id for ES
    * @param suffixDateKey the key where to find a suffix date for indexes
    * @param update        whether to upsert or not the items
    * @param parentIdKey   the key where to find the parent id for ES
    * @param bulkOptions   options of bulk
    * @param system        actor system
    * @tparam D the original type of the document
    * @return the indexes on which some documents have been indexed
    */
  override def bulk[D](items: Iterator[D],
                       toDocument: (D) => String,
                       idKey: Option[String],
                       suffixDateKey: Option[String],
                       suffixDatePattern: Option[String],
                       update: Option[Boolean],
                       delete: Option[Boolean],
                       parentIdKey: Option[String])(implicit bulkOptions: BulkOptions, system: ActorSystem): Set[String] =
    throw new UnsupportedOperationException

  override def countAsync(query: String, indices: immutable.Seq[String], types: immutable.Seq[String]): Future[CountResult] =
    throw new UnsupportedOperationException

  override def count(query: String, indices: immutable.Seq[String], types: immutable.Seq[String]): CountResult =
    throw new UnsupportedOperationException

  override def searchAsync(query: String, indices: immutable.Seq[String], types: immutable.Seq[String]): Future[SearchResult] =
    throw new UnsupportedOperationException

  override def searchAsync(sqlQuery: String)(
    implicit ec: ExecutionContext): Future[Option[SearchResult]] =
    throw new UnsupportedOperationException

  override def innerSearch(query: String, indices: immutable.Seq[String], types: immutable.Seq[String]): SearchResult =
    throw new UnsupportedOperationException

  override def innerSearch(sqlQuery: String): Option[SearchResult] =
    throw new UnsupportedOperationException

  override def multiSearch(sqlQueries: List[String]): Option[MultiSearchResult] =
    throw new UnsupportedOperationException

  override def get[U <: Timestamped](id: String, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], formats: Formats): Option[U] =
    elasticDocuments.get(id).asInstanceOf[Option[U]]

  override def getAsync[U <: Timestamped](id: String, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], ec: ExecutionContext, formats: Formats): Future[Option[U]] =
    Future.successful(elasticDocuments.get(id).asInstanceOf[Option[U]])

  override def getAll[U](sqlQuery: String)(implicit m: Manifest[U], formats: Formats): List[U] =
    elasticDocuments.getAll.toList.asInstanceOf[List[U]]

  override def getAllAsync[U](sqlQuery: String)(implicit m: Manifest[U], ec: ExecutionContext, formats: Formats
  ): Future[List[U]] =
    Future.successful(getAll(sqlQuery))

  override def multiGetAll[U](sqlQueries: List[String])(implicit m: Manifest[U], formats: Formats): List[List[U]] =
    throw new UnsupportedOperationException

  override def index[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], formats: Formats): String = {
    entity match {
      case _: T => elasticDocuments.createOrUpdate(entity.asInstanceOf[T])
      case _ =>
    }
    entity.uuid
  }

  override def indexAsync[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None)(
    implicit m: Manifest[U], ec: ExecutionContext, formats: Formats): Future[String] = {
    entity match {
      case _: T => elasticDocuments.createOrUpdate(entity.asInstanceOf[T])
      case _ =>
    }
    Future.successful(entity.uuid)
  }

  override def index(index: String, `type`: String, id: String, source: String): String =
    throw new UnsupportedOperationException

  override def indexAsync(index: String, `type`: String, id: String, source: String)(implicit ec: ExecutionContext): Future[String] =
    throw new UnsupportedOperationException

  override def update[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None, upsert: Boolean = true)(
    implicit m: Manifest[U], formats: Formats): String = {
    entity match {
      case _: T => elasticDocuments.createOrUpdate(entity.asInstanceOf[T])
      case _ =>
    }
    entity.uuid
  }

  override   def updateAsync[U <: Timestamped](entity: U, index: Option[String] = None, `type`: Option[String] = None, upsert: Boolean = true)(
    implicit m: Manifest[U], ec: ExecutionContext, formats: Formats): Future[String] = {
    entity match {
      case _: T => elasticDocuments.createOrUpdate(entity.asInstanceOf[T])
      case _ =>
    }
    Future.successful(entity.uuid)
  }

  override def update(index: String, `type`: String, id: String, source: String, upsert: Boolean): String = {
    logger.warn("MockElasticClient - {} not updated for {}", id, source)
    id
  }

  override def updateAsync(index: String, `type`: String, id: String, source: String, upsert: Boolean)(
    implicit ec: ExecutionContext): Future[String] = Future.successful(id)

  override def delete(uuid: String, index: String, `type`: String): Boolean = {
    if(elasticDocuments.get(uuid).isDefined){
      elasticDocuments.delete(uuid)
      true
    }
    else {
      false
    }
  }

  override def deleteAsync(uuid: String, index: String, `type`: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    Future.successful(delete(uuid, index, `type`))
  }

  override def refresh(index: String): Boolean = true

  override def flush(index: String, force: Boolean, wait: Boolean): Boolean = true
}

trait ElasticDocuments[T <: Timestamped] {

  private[this] var documents: Map[String, T] = Map()

  def createOrUpdate(entity: T) = {
    documents = documents.updated(entity.uuid, entity)
  }

  def delete(uuid: String): Unit = {
    documents = documents.filterNot((item: (String, T)) => item._1 == uuid)
  }

  def getAll: Iterable[T] = documents.values

  def get(uuid: String): Option[T] = documents.get(uuid)

}
