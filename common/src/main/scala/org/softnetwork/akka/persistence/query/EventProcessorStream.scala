package org.softnetwork.akka.persistence.query

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop, ActorSystem}

import akka.{NotUsed, Done}

import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.query.scaladsl.ReadJournal

import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.{Sink, RestartSource, Source}

import akka.stream.{KillSwitches, SharedKillSwitch, Materializer}
import com.typesafe.scalalogging.{Logger, StrictLogging}

import org.softnetwork.akka.message.Event

import org.softnetwork.akka.persistence.typed._

import akka.{actor => classic}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by smanciot on 16/05/2020.
  */
object EventProcessor {

  def apply(eventProcessorStream: EventProcessorStream[_]): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { ctx =>
      val killSwitch = KillSwitches.shared("eventProcessorSwitch")
      eventProcessorStream.runQueryStream(killSwitch)
      Behaviors.receiveSignal[Nothing] {
        case (_, PostStop) =>
          ctx.log.info(s"Stopping stream ${eventProcessorStream.eventProcessorId} for tag [${eventProcessorStream.tag}] ")
          killSwitch.shutdown()
          Behaviors.same
      }
    }
  }

}

trait JournalProvider extends ReadJournal {

  implicit def classicSystem: classic.ActorSystem

  def eventProcessorId: String

  def tag: String

  protected def logger: Logger

  protected def startOffset() = Offset.sequence(0L)

  protected def initJournalProvider(): Unit = {}

  /**
    *
    * @param tag - tag
    * @param offset - offset
    * @return
    */
  protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed]

  /**
    * Read current offset
    *
    * @return
    */
  protected def readOffset(): Future[Offset] = Future.successful(Offset.sequence(0L))

  /**
    * Persist current offset
    *
    * @param offset - current offset
    * @return
    */
  protected def writeOffset(offset: Offset): Future[Done] = Future.successful(Done)

}

trait EventProcessorStream[E <: Event] extends StrictLogging { _: JournalProvider =>

  implicit def system: ActorSystem[_]

  implicit lazy val ec = system.executionContext

  override implicit lazy val classicSystem: classic.ActorSystem = system

  implicit def mat: Materializer = Materializer(classicSystem)

  def eventProcessorId: String

  def tag: String

  protected def init(): Unit = {}

  /**
    *
    * Processing event
    *
    * @param event - event to process
    * @param persistenceId - persistence id
    * @param sequenceNr - sequence number
    * @return
    */
  protected def processEvent(event: E, persistenceId: PersistenceId, sequenceNr: Long): Future[Done]

  /**
    *
    * @param tag - tag
    * @param offset - offset
    * @return
    */
  protected def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed]

  private[this] def processEventsByTag(offset: Offset): Source[Offset, NotUsed] = {
    eventsByTag(tag, offset).mapAsync(1) { eventEnvelope =>
      eventEnvelope.event match {
        case event: E =>
          processEvent(
            event,
            PersistenceId.ofUniqueId(eventEnvelope.persistenceId),
            eventEnvelope.sequenceNr
          ).map(_ => eventEnvelope.offset)
        case other =>
          logger.error("Unexpected event [{}]", other)
          Future.failed(new IllegalArgumentException(s"Unexpected event [${other.getClass.getName}]"))
      }
    }
  }

  final def runQueryStream(killSwitch: SharedKillSwitch): Unit = {
    init()
    RestartSource
      .withBackoff(minBackoff = 500.millis, maxBackoff = 20.seconds, randomFactor = 0.1) { () =>
        Source.futureSource {
          initJournalProvider()
          readOffset().map { offset =>
            logger.info("Starting stream {} for tag [{}] from offset [{}]", eventProcessorId, tag, offset)
            processEventsByTag(offset)
              // groupedWithin can be used here to improve performance by reducing number of offset writes,
              // with the trade-off of possibility of more duplicate events when stream is restarted
              .mapAsync(1)(writeOffset)
          }
        }
      }
      .via(killSwitch.flow)
      .runWith(Sink.ignore)
  }

}
