package org.softnetwork.akka.persistence.jdbc.query

import akka.{NotUsed, Done}

import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{Sequence, PersistenceQuery, EventEnvelope, Offset}

import akka.stream.scaladsl.Source
import org.softnetwork.akka.persistence.jdbc.util.{Postgres, Db}

import slick.jdbc.JdbcBackend.Session

import scala.concurrent.Future

import org.softnetwork.akka.persistence.query.JournalProvider

import scala.util.{Try, Success, Failure}

/**
  * Created by smanciot on 16/05/2020.
  */
trait JdbcJournalProvider extends JournalProvider { _: Db =>

  def offsetSchema: String

  def offsetTable: String = "event_processor_offsets"

  private[this] implicit lazy val session: Session = withDatabase(_.createSession())

  private[this] lazy val query = PersistenceQuery(classicSystem).readJournalFor[JdbcReadJournal](
    JdbcReadJournal.Identifier
  )

  override protected def initJournalProvider(): Unit = {
    classicSystem.registerOnTermination(() => session.close())
    val metaData = session.metaData
    val tables = metaData.getTables(null, "public", "event_processor_offsets", Array[String]("TABLE"))
    if(!tables.next()){
      Try{
        session.createStatement().executeUpdate(
          s"CREATE TABLE IF NOT EXISTS $offsetSchema.$offsetTable (" +
            "event_processor_id VARCHAR(255) NOT NULL, " +
            "tag VARCHAR(255) NOT NULL, " +
            "sequence_number BIGINT NOT NULL, " +
            "PRIMARY KEY(event_processor_id, tag)" +
            ");")
      } match {
        case Success(_) =>
          logger.info(s"CREATE Offset TABLE $offsetSchema.$offsetTable")
        case Failure(f) =>
          logger.error(s"FAILED TO CREATE Offset TABLE -> ${f.getMessage}", f)
          throw f
      }
    }
  }

  /**
    *
    * @param tag    - tag
    * @param offset - offset
    * @return
    */
  override protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    query.eventsByTag(tag, offset)

  /**
    * Read current offset
    *
    * @return
    */
  override protected def readOffset(): Future[Offset] = {
    Try{
      val statement = session.prepareStatement(
        s"SELECT sequence_number FROM $offsetSchema.$offsetTable WHERE event_processor_id=? AND tag=?"
      )
      statement.setString(1, eventProcessorId)
      statement.setString(2, tag)
      val resultSet = statement.executeQuery()
      val sequenceNumber =
        if(resultSet.next()){
          Some(resultSet.getLong("sequence_number"))
        }
        else{
          None
        }
      statement.close()
      sequenceNumber
    } match {
      case Success(sequenceNumber) =>
        sequenceNumber match {
          case Some(s) =>
            logger.info(s"SELECT Offset ($eventProcessorId, $tag) -> $s")
            Future.successful(Offset.sequence(s))
          case _ =>
            Try{
              val statement = session.prepareStatement(
               s"INSERT INTO $offsetSchema.$offsetTable (event_processor_id, tag, sequence_number) VALUES(?, ?, 0)"
              )
              statement.setString(1, eventProcessorId)
              statement.setString(2, tag)
              statement.executeUpdate()
            } match {
              case Success(s) =>
                if(s != 1)
                  logger.error(s"FAILED TO INSERT Offset ($eventProcessorId, $tag, 0) -> $s")
                Future.successful(startOffset())
              case Failure(f) => Future.failed(f)
            }
        }
      case Failure(f) =>
        logger.error(s"FAILED TO SELECT Offset ($eventProcessorId, $tag) -> ${f.getMessage}", f)
        Future.failed(f)
    }
  }

  /**
    * Persist current offset
    *
    * @param offset - current offset
    * @return
    */
  override protected def writeOffset(offset: Offset): Future[Done] = {
    offset match {
      case Sequence(value) =>
        logger.info(s"UPDATING Offset ($eventProcessorId, $tag) -> $value")
        Try{
          val statement =
              session.prepareStatement(
                s"UPDATE $offsetSchema.$offsetTable set sequence_number=? WHERE event_processor_id=? AND tag=?"
              )
          statement.setLong(1, value)
          statement.setString(2, eventProcessorId)
          statement.setString(3, tag)
          statement.executeUpdate()
        } match {
          case Success(s) =>
            if(s != 1)
              logger.error(s"FAILED TO UPDATE Offset ($eventProcessorId, $tag, $value) -> $s")
            Future.successful(Done)
          case Failure(f) => Future.failed(f)
        }
      case other => Future.failed(new Exception(s"JdbcJournalProvider does not support Offset ${other.getClass}"))
    }
  }

}

trait JdbcPostgresJournalProvider extends JdbcJournalProvider with Postgres {
  override val offsetSchema: String = "public"
}

trait MockJdbcPostgresJournalProvider extends JdbcPostgresJournalProvider{
  override protected def initJournalProvider(): Unit = {}

  /**
    * Read current offset
    *
    * @return
    */
  override protected def readOffset(): Future[Offset] = Future.successful(startOffset())

  /**
    * Persist current offset
    *
    * @param offset - current offset
    * @return
    */
  override protected def writeOffset(offset: Offset): Future[Done] = Future.successful(Done)
}