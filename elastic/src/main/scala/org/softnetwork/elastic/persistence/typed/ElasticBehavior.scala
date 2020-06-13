package org.softnetwork.elastic.persistence.typed

import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.{ActorSystem, ActorRef}
import org.softnetwork.ManifestWrapper
import org.softnetwork.akka.message._

import org.softnetwork.akka.persistence.typed._
import org.softnetwork.akka.serialization._

import akka.persistence.typed.scaladsl.Effect
import com.typesafe.scalalogging.StrictLogging

import io.searchbox.core.search.aggregation.RootAggregation

import org.slf4j.Logger

import org.softnetwork.akka.persistence.typed.EntityBehavior

import org.softnetwork.elastic.client.ElasticApi

import org.softnetwork.elastic.message._

import org.softnetwork.akka.model.Timestamped
import org.softnetwork.elastic.persistence.query.ElasticProvider

import org.softnetwork.elastic.sql.ElasticQuery

import scala.concurrent.{Await, Future, Promise}

import scala.language.implicitConversions
import scala.language.postfixOps

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 16/05/2020.
  */
trait ElasticBehavior[S  <: Timestamped] extends EntityBehavior[ElasticCommand, S, ElasticEvent, ElasticResult] 
  with ManifestWrapper[S] with ElasticProvider[S] with StrictLogging {_: ElasticApi[S] =>

  private[this] val defaultAtMost = 10.second

  override def init(system: ActorSystem[_])(implicit tTag: ClassTag[ElasticCommand], m: Manifest[S]): Unit = {
    logger.info(s"Initializing ${TypeKey.name}")
    super.init(system)
    initIndex()
  }

  /**
    *
    * @param entityId - entity identity
    * @param state - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @return effect
    */
  override def handleCommand(
                              entityId: String,
                              state: Option[S],
                              command: ElasticCommand,
                              replyTo: Option[ActorRef[ElasticResult]],
                              self: ActorRef[ElasticCommand])(
                              implicit system: ActorSystem[_], log: Logger, m: Manifest[S], timers: TimerScheduler[ElasticCommand]
                            ): Effect[ElasticEvent, Option[S]] = {
    command match {

      case cmd: CreateDocument[S] =>
        import cmd._
        Effect.persist[ElasticEvent, Option[S]](DocumentCreatedEvent(document))
          .thenRun(state => DocumentCreated(document.uuid) ~> replyTo)

      case cmd: UpdateDocument[S] =>
        import cmd._
        Effect.persist[ElasticEvent, Option[S]](DocumentUpdatedEvent(document, upsert))
          .thenRun(state => DocumentUpdated(document.uuid) ~> replyTo)

      case cmd: UpsertDocument =>
        import cmd._
        Effect.persist[ElasticEvent, Option[S]](
          DocumentUpsertedEvent(
            id,
            data
          )
        ).thenRun(state => DocumentUpserted(entityId) ~> replyTo)

      case cmd: DeleteDocument =>
        import cmd._
        Effect.persist[ElasticEvent, Option[S]](DocumentDeletedEvent(id))
          .thenRun(state => DocumentDeleted ~> replyTo).thenStop()

      case cmd: LoadDocument =>
        state match {
          case Some(s) => Effect.none.thenRun(state => DocumentLoaded(s) ~> replyTo)
          case _       => Effect.none.thenRun(state => DocumentNotFound ~> replyTo)
        }

      case cmd: LoadDocumentAsync =>
        state match {
          case Some(s) => Effect.none.thenRun(state => DocumentLoaded(s) ~> replyTo)
          case _       => Effect.none.thenRun(state => DocumentNotFound ~> replyTo)
        }

      case cmd: LookupDocuments =>
        import cmd._
        implicit val jestClient = apply()
        Try(getAll[S](sqlQuery)) match {
          case Success(documents) =>
            documents match {
              case Nil => Effect.none.thenRun(state => NoResultsFound ~> replyTo)
              case _   => Effect.none.thenRun(state => DocumentsFound[S](documents) ~> replyTo)
            }
          case Failure(f) =>
            log.error(f.getMessage, f)
            jestClient.close()
            Effect.none.thenRun(state => NoResultsFound ~> replyTo)
        }

      case cmd: Count =>
        import cmd._
        implicit val jestClient = apply()
        implicit val ec = system.executionContext
        val futures = for (elasticCount <- ElasticQuery.count(query)) yield {
          val promise: Promise[CountResponse] = Promise()
          import collection.immutable.Seq
          val _field = elasticCount.field
          val _sourceField = elasticCount.sourceField
          val _agg = elasticCount.agg
          val _query = elasticCount.query
          val _sources = elasticCount.sources
          _sourceField match {
            case "_id" =>
              countAsync(
                _query,
                Seq(_sources: _*),
                Seq.empty[String]
              ).onComplete {
                case Success(result) => promise.success(new CountResponse(_field, result.getCount.toInt, None))
                case Failure(f) =>
                  logger.error(f.getMessage, f.fillInStackTrace())
                  promise.success(new CountResponse(_field, 0, Some(f.getMessage)))
              }
            case _ =>
              searchAsync(
                _query,
                Seq(_sources: _*),
                Seq.empty[String]
              ).onComplete {
                case Success(result) =>
                  val agg = _agg.split("\\.").last

                  val itAgg = _agg.split("\\.").iterator

                  var root =
                    if (elasticCount.nested)
                      result.getAggregations.getAggregation(itAgg.next(), classOf[RootAggregation])
                    else
                      result.getAggregations

                  if (elasticCount.filtered) {
                    root = root.getAggregation(itAgg.next(), classOf[RootAggregation])
                  }

                  promise.success(
                    new CountResponse(
                      _field,
                      if (elasticCount.distinct)
                        root.getCardinalityAggregation(agg).getCardinality.toInt
                      else
                        root.getValueCountAggregation(agg).getValueCount.toInt,
                      None
                    )
                  )

                case Failure(f) =>
                  logger.error(f.getMessage, f.fillInStackTrace())
                  promise.success(new CountResponse(_field, 0, Some(f.getMessage)))
              }
          }
          promise.future
        }
        Effect.none.thenRun(state => (Try(Await.result(Future.sequence(futures.toSeq), defaultAtMost)) match {
          case Success(s) => ElasticCountResult(s)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            CountFailure
        }) ~> replyTo)

      case cmd: BulkUpdateDocuments =>
        import cmd._
        import org.softnetwork.elastic.client._
        implicit val bulkOptions = BulkOptions(index, `type`)
        Try(
          bulk[Map[String, Any]](
            documents.iterator,
            item => serialization.write(item),
            idKey = Some("uuid"),
            update = Some(true),
            delete = Some(false)
          )(bulkOptions, system)
        ) match {
          case Success(_) => Effect.none.thenRun(state => DocumentsBulkUpdated ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(state => BulkUpdateDocumentsFailure ~> replyTo)
        }

      case cmd: BulkDeleteDocuments =>
        import cmd._
        import org.softnetwork.elastic.client._
        implicit val bulkOptions = BulkOptions(index, `type`)
        Try(
          bulk[Map[String, Any]](
            documents.iterator,
            item => serialization.write(item),
            idKey = Some("uuid"),
            update = Some(false),
            delete = Some(true)
          )(bulkOptions, system)
        ) match {
          case Success(_) => Effect.none.thenRun(state => DocumentsBulkDeleted ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(state => BulkDeleteDocumentsFailure ~> replyTo)
        }

      case cmd: RefreshIndex =>
        implicit val jestClient = apply()
        Try(refresh(cmd.index.getOrElse(index))) match {
          case Success(_) => Effect.none.thenRun(state => IndexRefreshed ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(state => RefreshIndexFailure ~> replyTo)
        }

      case cmd: FlushIndex =>
        implicit val jestClient = apply()
        Try(flush(cmd.index.getOrElse(index))) match {
          case Success(_) => Effect.none.thenRun(state => IndexFlushed ~> replyTo)
          case Failure(f) =>
            logger.error(f.getMessage, f.fillInStackTrace())
            Effect.none.thenRun(_ => FlushIndexFailure ~> replyTo)
        }

      case _ => Effect.none.thenRun(_ => ElasticUnknownCommand ~> replyTo)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[S], event: ElasticEvent)(
    implicit system: ActorSystem[_], log: Logger, m: Manifest[S]): Option[S] = {
    event match {
      case evt: CrudEvent => handleElasticCrudEvent(state, evt)
      case _ => super.handleEvent(state, event)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - elastic event to hanlde
    * @return new state
    */
  private[this] def handleElasticCrudEvent(state: Option[S], event: CrudEvent)(
    implicit system: ActorSystem[_], log: Logger, m: Manifest[S]): Option[S] = {
    event match {
      case e: Created[S] =>
        import e._
        if(createDocument(document)){
          Some(document)
        }
        else{
          state
        }

      case e: Updated[S] =>
        import e._
        if(updateDocument(document, upsert)){
          Some(document)
        }
        else{
          state
        }

      case e: Upserted =>
        import e._
        if(upsertDocument(uuid, data)){
          loadDocument(uuid)
        }
        else{
          state
        }

      case e: Deleted =>
        import e._
        if(deleteDocument(uuid)){
          emptyState
        }
        else{
          state
        }

      case _ => state
    }
  }
}
