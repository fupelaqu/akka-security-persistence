package org.softnetwork.akka.persistence.query

import _root_.akka.Done
import _root_.akka.persistence.typed.PersistenceId
import com.typesafe.scalalogging.StrictLogging

import org.softnetwork._

import org.softnetwork.akka.message._

import org.softnetwork.akka.model.Timestamped

import scala.concurrent.Future

/**
  * Created by smanciot on 16/05/2020.
  */
trait State2ExternalProcessorStream[T <: Timestamped, E <: CrudEvent] extends EventProcessorStream[E]
  with ManifestWrapper[T] with StrictLogging {_: JournalProvider with PersistenceProvider[T] =>

  def externalProcessor: String

  lazy val tag: String = s"${getType[T](manifestWrapper.wrapped)}-to-$externalProcessor"

  lazy val eventProcessorId: String = tag

  /**
    *
    * Processing event
    *
    * @param event         - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr    - sequence number
    * @return
    */
  override protected def processEvent(event: E, persistenceId: PersistenceId, sequenceNr: Long): Future[Done] = {
    event match {

      case evt: Created[_] =>
        import evt._
        if(!createDocument(document.asInstanceOf[T])(manifestWrapper.wrapped)){
          logger.error("document {} has not be created by {}", document.uuid, eventProcessorId)
        }

      case evt: Updated[_] =>
        import evt._
        if(!updateDocument(document.asInstanceOf[T])(manifestWrapper.wrapped)){
          logger.error("document {} has not be updated by {}", document.uuid, eventProcessorId)
        }

      case evt: Deleted =>
        import evt._
        if(!deleteDocument(uuid)){
          logger.error("document {} has not be deleted by {}", uuid, eventProcessorId)
        }

      case evt: Upserted =>
        if(!upsertDocument(evt.uuid, evt.data)){
          logger.error("document {} has not been upserted by {}", evt.uuid, eventProcessorId)
        }

      case other => logger.warn("{} does not support event [{}]", eventProcessorId, other.getClass)
    }

    Future.successful(Done)
  }

}
