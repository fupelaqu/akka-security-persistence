package org.softnetwork.akka.persistence.query

import org.softnetwork.akka.model.Timestamped

/**
  * Created by smanciot on 16/05/2020.
  */
trait PersistenceProvider[T <: Timestamped] {

  /**
    * Creates the unerlying document to the external system
    * 
    * @param document - the document to create
    * @param m - implicit manifest for T
    * @return whether the operation is successful or not
    */
  def createDocument(document: T)(implicit m: Manifest[T]): Boolean

  /**
    * Updates the unerlying document to the external system
    *
    * @param document - the document to update
    * @param upsert - whether or not to create the underlying document if it does not exist in the external system
    * @param m - implicit manifest for T
    * @return whether the operation is successful or not
    */
  def updateDocument(document: T, upsert: Boolean = true)(implicit m: Manifest[T]): Boolean

  /**
    * Upserts the unerlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to upsert
    * @param data - the document data
    * @return whether the operation is successful or not
    */
  def upsertDocument(uuid: String, data: String): Boolean

  /**
    * Deletes the unerlying document referenced by its uuid to the external system
    *
    * @param uuid - the uuid of the document to delete
    * @return whether the operation is successful or not
    */
  def deleteDocument(uuid: String): Boolean

  /**
    * Load the document referenced by its uuid
    *
    * @param uuid - the document uuid
    * @return the document retrieved, None otherwise
    */
  def loadDocument(uuid: String): Option[T] = None

  /**
    * Search documents
    *
    * @param query - the search query
    * @return the documents founds or an empty list otherwise
    */
  def searchDocuments(query: String): List[T] = List.empty

}
