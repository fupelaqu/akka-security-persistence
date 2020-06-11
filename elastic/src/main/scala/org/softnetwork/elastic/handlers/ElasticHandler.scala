package org.softnetwork.elastic.handlers

import akka.actor.typed.ActorSystem
import org.softnetwork.akka.handlers.EntityHandler
import org.softnetwork.akka.message._
import org.softnetwork.akka.persistence.typed.CommandTypeKey

import org.softnetwork.elastic.message._

import org.softnetwork.akka.model.Timestamped

import scala.language.existentials

/**
  * Created by smanciot on 02/05/2020.
  */
trait ElasticHandler[T <: Timestamped] extends EntityHandler[ElasticCommand, ElasticResult] {
  _: CommandTypeKey[ElasticCommand] =>
  override protected def command2Request(command: ElasticCommand): Request =
    replyTo => ElasticCommandWrapper(command, replyTo)
}

trait ElasticDao[T <: Timestamped] {_: ElasticHandler[T] =>

  def create(document: T)(implicit system: ActorSystem[_], m: Manifest[T]) = {
    this !? CreateDocument(document) match {
      case r: DocumentCreated => Left(r)
      case other => Right(other)
    }
  }

  def update(document: T)(implicit system: ActorSystem[_], m: Manifest[T]) = {
    this !? UpdateDocument(document) match {
      case r: DocumentUpdated => Left(r)
      case other => Right(other)
    }
  }

  def upsert(id: String, data: Map[String, Any])(implicit system: ActorSystem[_]) = {
    this !? UpsertDocument(id, data) match {
      case r: DocumentUpserted => Left(r)
      case other => Right(other)
    }
  }

  def delete(id: String)(implicit system: ActorSystem[_], m: Manifest[T]) = {
    this !? DeleteDocument(id) match {
      case r: DocumentDeleted => Left(r)
      case other => Right(other)
    }
  }

  def load(id: String)(implicit system: ActorSystem[_]) = {
    this !? LoadDocument(id) match {
      case r: DocumentLoaded[_] => Left(r)
      case other => Right(other)
    }
  }

  def search(query: String)(implicit system: ActorSystem[_]) = {
    this !? LookupDocuments(query) match {
      case r: DocumentsFound[_] => Left(r)
      case other => Right(other)
    }
  }

  def count(query: String)(implicit system: ActorSystem[_]) = {
    this !? Count(query) match {
      case r: CountResult => Left(r)
      case other => Right(other)
    }
  }

  def bulkUpdate(documents: List[Map[String, Any]])(implicit system: ActorSystem[_]) = {
    this !? BulkUpdateDocuments(documents) match {
      case r: DocumentsBulkUpdated.type => Left(r)
      case other => Right(other)
    }
  }

  def bulkDelete(documents: List[Map[String, Any]])(implicit system: ActorSystem[_]) = {
    this !? BulkDeleteDocuments(documents) match {
      case r: DocumentsBulkDeleted.type => Left(r)
      case other => Right(other)
    }
  }

  def flush(index: Option[String])(implicit system: ActorSystem[_]) = {
    this !? FlushIndex(index) match {
      case r: IndexFlushed.type => Left(r)
      case other => Right(other)
    }
  }

  def refresh(index: Option[String])(implicit system: ActorSystem[_]) = {
    this !? RefreshIndex(index) match {
      case r: IndexRefreshed.type => Left(r)
      case other => Right(other)
    }
  }

}
