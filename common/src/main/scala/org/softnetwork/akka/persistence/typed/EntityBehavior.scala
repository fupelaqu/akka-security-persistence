package org.softnetwork.akka.persistence.typed

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler, Behaviors}
import akka.actor.typed.{ActorSystem, SupervisorStrategy, Behavior, ActorRef}

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

import akka.persistence.typed._
import akka.persistence.typed.scaladsl.{Recovery, EventSourcedBehavior, Effect, RetentionCriteria}

import org.softnetwork.akka.message._
import org.softnetwork.akka.model._
import org.softnetwork.akka.persistence.PersistenceTools

import scala.concurrent.duration._

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Created by smanciot on 16/05/2020.
  */
trait CommandTypeKey[C <: Command] {
  def TypeKey(implicit tTag: ClassTag[C]): EntityTypeKey[C]
}

trait EntityBehavior[C <: Command, S <: State, E <: Event, R <: CommandResult] extends CommandTypeKey[C] {
  type W = CommandWrapper[C, R] with C

  type WR = CommandWithReply[R] with C

  type CT = CronTabCommand with C

  /** number of events received before generating a snapshot - should be configurable **/
  def snapshotInterval: Int = 100

  def persistenceId: String

  final def TypeKey(implicit tTag: ClassTag[C]): EntityTypeKey[C] =
    EntityTypeKey[C](s"$persistenceId-${PersistenceTools.env}")

  val emptyState: Option[S] = None

  private[this] var initialized = false

  def init(system: ActorSystem[_])(implicit tTag: ClassTag[C], m: Manifest[S]): Unit = {
    if(!initialized){
      EntitySystemLocator(system)
      ClusterSharding(system)init Entity(TypeKey) { entityContext =>
        this(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      }
      initialized = true
    }
  }

  /**
    *
    * @return scheduler(s) to sent messages repeatedly to the `self` actor with a fixed `delay` between messages.
    */
  protected def schedules: Seq[Schedule[C]] = Seq.empty

  /**
    *
    * Set event tags, which will be used in persistence query
    *
    * @param entityId - entity id
    * @param event - the event to tag
    * @return event tags
    */
  protected def tagEvent(entityId: String, event: E): Set[String] = Set.empty

  /**
    *
    * @param system - actor system
    * @param subscriber - the `self` actor
    * @param tTag - class tag of the commands supported by this actor
    */
  protected def subscribe(system: ActorSystem[_], subscriber: ActorRef[C])(implicit tTag: ClassTag[C]): Unit = {}

  implicit def resultToMaybeReply(r: R): MaybeReply = new MaybeReply {
    def apply() = {
      case Some(subscriber) => subscriber ! r
      case _ =>
    }
  }

  sealed trait MaybeReply {
    def apply(): Option[ActorRef[R]] => Unit
    final def ~>(replyTo: Option[ActorRef[R]]): Unit = apply()(replyTo)
  }

  final def apply(entityId: String, persistenceId: PersistenceId)(
    implicit tTag: ClassTag[C], m: Manifest[S]): Behavior[C] = {
    Behaviors.withTimers((timers) => { schedules.foreach((schedule) => schedule.timer(timers))
      Behaviors.setup { context =>
        context.log.info(s"Starting $persistenceId")
        subscribe(context.system, context.self)
        EventSourcedBehavior[C, E, Option[S]](
          persistenceId = persistenceId,
          emptyState = emptyState,
          commandHandler = { (state, command) =>
            context.log.debug(s"handling command $command for ${TypeKey.name} $entityId")
            command match {
              case w: W => handleCommand(entityId, state, w.command, Some(w.replyTo), timers)(context)
              case wr: WR => handleCommand(entityId, state, wr, Some(wr.replyTo), timers)(context)
              case ct: CT =>
                val effect = handleCommand(entityId, state, ct, None, timers)(context)
                implicitly[Schedule[C]](ct).timer(timers)
                effect
              case c: C => handleCommand(entityId, state, c, None, timers)(context)
              case _ => Effect.unhandled
            }
          },
          eventHandler = { (state, event) =>
            context.log.debug(s"handling event $event for ${TypeKey.name} $entityId")
            handleEvent(state, event)(context)
          }
        )
          .onPersistFailure(
            SupervisorStrategy.restartWithBackoff(minBackoff = 10.seconds, maxBackoff = 60.seconds, randomFactor = 0.1)
          )
          /* Persistent actors can save snapshots of internal state every N events or when a given predicate of the state
          is fulfilled (snapshotWhen). */
          .withRetention(
          RetentionCriteria.snapshotEvery(numberOfEvents = snapshotInterval, keepNSnapshots = 2)
            .withDeleteEventsOnSnapshot /* after a snapshot has been successfully stored, a delete of the events
          (journaled by a single event sourced actor) up until the sequence number of the data held by that snapshot
          can be issued */
        )
          /* During recovery, the persistent actor is using the latest saved snapshot to initialize the state.
          Thereafter the events after the snapshot are replayed using the event handler to recover the persistent actor
          to its current (i.e. latest) state. */
          .withRecovery(Recovery.withSnapshotSelectionCriteria(SnapshotSelectionCriteria.latest))
          .receiveSignal {
            case (state, _: RecoveryFailed) => context.log.error(s"Recovery failed for ${TypeKey.name} $entityId")
            case (state, _: RecoveryCompleted) => context.log.info(s"Recovery completed for ${TypeKey.name} $entityId")
            case (state, _: SnapshotCompleted) => context.log.info(s"Snapshot completed for ${TypeKey.name} $entityId")
            case (state, _: SnapshotFailed) => context.log.warn(s"Snapshot failed for ${TypeKey.name} $entityId")
            case (state, _: DeleteSnapshotsFailed) => context.log.warn(s"Snapshot deletion failed for ${TypeKey.name} $entityId")
            case (state, _: DeleteEventsFailed) => context.log.warn(s"Events deletion failed for ${TypeKey.name} $entityId")
          }
          .withTagger(event => tagEvent(entityId, event))
      }
    })
  }

  /**
    *
    * @param entityId - entity identity
    * @param state - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @param timers - scheduled messages associated with this entity behavior
    * @return effect
    */
  def handleCommand(entityId: String, state: Option[S], command: C, replyTo: Option[ActorRef[R]], timers: TimerScheduler[C])(
    implicit context: ActorContext[C]): Effect[E, Option[S]] =
    command match {
      case _    => Effect.unhandled
    }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  def handleEvent(state: Option[S], event: E)(implicit context: ActorContext[C]): Option[S] =
    event match {
      case _  => state
    }
}
