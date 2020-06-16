package org.softnetwork.notification.peristence.typed

import java.util.Date

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.ActorRef

import akka.persistence.typed.scaladsl.Effect

import org.slf4j.Logger

import org.softnetwork.akka.persistence.typed.{Schedule, EntityBehavior}

import org.softnetwork.notification.handlers._

import org.softnetwork.notification.message._

import org.softnetwork.notification.model._

import scala.language.{implicitConversions, postfixOps}

import scala.concurrent.duration._

/**
  * Created by smanciot on 13/04/2020.
  */
sealed trait NotificationBehavior[T <: Notification] extends EntityBehavior[
  NotificationCommand, T, NotificationEvent, NotificationCommandResult] { self: NotificationProvider[T] =>

  private[this] val provider: NotificationProvider[T] = this

  private case object NotificationTimerKey

  override protected val schedules =
    Seq(
      Schedule(
        NotificationTimerKey,
        NotificationTimeout,
        Some(1.minute)
      )
    )

  /**
    *
    * @param entityId - entity identity
    * @param state   - current state
    * @param command - command to handle
    * @param replyTo - optional actor to reply to
    * @return effect
    */
  override def handleCommand( entityId: String,
                              state: Option[T],
                              command: NotificationCommand,
                              replyTo: Option[ActorRef[NotificationCommandResult]],
                              timers: TimerScheduler[NotificationCommand])(
    implicit context: ActorContext[NotificationCommand]): Effect[NotificationEvent, Option[T]] = {
    implicit val log = context.log
    command match {

      case cmd: AddNotification[T] =>
        import cmd._
        (notification match {
          case n: Mail => Some(MailRecordedEvent(n))
          case n: SMS => Some(SMSRecordedEvent(n))
          case n: Push => Some(PushRecordedEvent(n))
          case _ => None
        }) match {
          case Some(event) => Effect.persist(event).thenRun(_ => NotificationAdded(entityId) ~> replyTo)
          case _ => Effect.unhandled
        }

      case cmd: RemoveNotification =>
        Effect.persist[NotificationEvent, Option[T]](
          NotificationRemovedEvent(
            entityId
          )
        ).thenRun(_ => NotificationRemoved ~> replyTo).thenStop()

      case cmd: SendNotification[T] => sendNotification(entityId, cmd.notification, replyTo)

      case cmd: ResendNotification => state match {
        case Some(s) =>
          import s._
          import NotificationStatus._
          status match {
            case Sent      => Effect.none.thenRun(_ => NotificationSent(entityId) ~> replyTo)
            case Delivered => Effect.none.thenRun(_ => NotificationDelivered(entityId) ~> replyTo)
            case _         => sendNotification(entityId, s, replyTo)
          }
        case _ => Effect.none.thenRun(_ => NotificationNotFound ~> replyTo)
      }

      case cmd: GetNotificationStatus =>
        state match {
          case Some(s) =>
            import s._
            import NotificationStatus._
            status match {
              case Sent        => Effect.none.thenRun(_ => NotificationSent(entityId) ~> replyTo)
              case Delivered   => Effect.none.thenRun(_ => NotificationDelivered(entityId) ~> replyTo)
              case Rejected    => Effect.none.thenRun(_ => NotificationRejected(entityId) ~> replyTo)
              case Undelivered => Effect.none.thenRun(_ => NotificationUndelivered(entityId) ~> replyTo)
              case _           => ack(entityId, s, replyTo) // Pending
            }
          case _ => Effect.none.thenRun(_ => NotificationNotFound ~> replyTo)
        }

      case NotificationTimeout =>
        state match {
          case Some(s) =>
            import s._
            import NotificationStatus._
            status match {
              case Sent      =>
                timers.cancel(NotificationTimerKey)
                Effect.none
              case Delivered =>
                timers.cancel(NotificationTimerKey)
                Effect.none
              case Pending   => sendNotification(entityId, s, None)
              case _         =>
                // Rejected | Undelivered
                if(maxTries > 0 && nbTries > maxTries){
                  timers.cancel(NotificationTimerKey)
                  Effect.none
                }
                else{
                  sendNotification(entityId, s, None)
                }
            }
          case _ => Effect.none
        }

      case _ => super.handleCommand(entityId, state, command, replyTo, timers)
    }
  }

  /**
    *
    * @param state - current state
    * @param event - event to hanlde
    * @return new state
    */
  override def handleEvent(state: Option[T], event: NotificationEvent)(
    implicit context: ActorContext[NotificationCommand]): Option[T] = {
    import context._
    event match {
      case evt: NotificationRecordedEvent[T] =>
        log.info(s"Recorded $persistenceId ${evt.uuid}")
        Some(evt.notification)

      case evt: NotificationRemovedEvent =>
        log.info(s"Removed $persistenceId ${evt.uuid}")
        emptyState

      case _  =>  super.handleEvent(state, event)
    }
  }

  private[this] def ack(
           _uuid: String,
           notification: T,
           replyTo: Option[ActorRef[NotificationCommandResult]])(implicit log: Logger
  ): Effect[NotificationEvent, Option[T]] = {
    import notification._
    val ack: NotificationAck = ackUuid match {
      case Some(s) =>
        import NotificationStatus._
        status match {
          case Pending => provider.ack(notification) // we only call the provider api if the notification is pending
          case _       => NotificationAck(ackUuid, results, new Date())
        }
      case _       => NotificationAck(None, results, new Date())
    }
    (notification match {
      case n: Mail =>
        Some(
          MailRecordedEvent(n.copyWithAck(ack).asInstanceOf[Mail])
        )
      case n: SMS =>
        Some(
          SMSRecordedEvent(n.copyWithAck(ack).asInstanceOf[SMS])
        )
      case n: Push =>
        Some(
          PushRecordedEvent(n.copyWithAck(ack).asInstanceOf[Push])
        )
      case _ => None
    }) match {
      case Some(event) =>
        Effect.persist(event)
          .thenRun(state => {
            import NotificationStatus._
            ack.status match {
              case Rejected    => NotificationRejected(_uuid)
              case Undelivered => NotificationUndelivered(_uuid)
              case Sent        => NotificationSent(_uuid)
              case Delivered   => NotificationDelivered(_uuid)
              case _           => NotificationPending(_uuid)
            }
          }
        ~> replyTo)
      case _ => Effect.unhandled
    }
  }

  private[this] def sendNotification(
                        _uuid: String,
                        notification: T,
                        replyTo: Option[ActorRef[NotificationCommandResult]])(implicit log: Logger
  ): Effect[NotificationEvent, Option[T]] = {
    import notification._
    import NotificationStatus._
    val maybeSent = status match {
      case Sent        => None
      case Delivered   => None
      case Pending     =>
        notification.deferred match {
          case Some(deferred) if deferred.after(new Date()) => None
          case _ => Some(provider.send(notification))
        }
      case _ =>
        // Undelivered or Rejected
        if(maxTries > 0 && nbTries > maxTries){
          None
        }
        else
          Some(provider.send(notification))
    }
    (notification match {
      case n: Mail =>
        Some(
          MailRecordedEvent(
            maybeSent match {
              case Some(ack) => n.incNbTries().copyWithAck(ack).asInstanceOf[Mail]
              case _ => n
            }
          )
        )
      case n: SMS =>
        Some(
          SMSRecordedEvent(
            maybeSent match {
              case Some(ack) => n.incNbTries().copyWithAck(ack).asInstanceOf[SMS]
              case _ => n
            }
          )
        )
      case n: Push =>
        Some(
          PushRecordedEvent(
            maybeSent match {
              case Some(ack) => n.incNbTries().copyWithAck(ack).asInstanceOf[Push]
              case _ => n
            }
          )
        )
      case _ => None
    }) match {
      case Some(event) =>
        Effect.persist(event)
          .thenRun(state => {
            import NotificationStatus._
            event.notification.status match {
              case Rejected    => NotificationRejected(_uuid)
              case Undelivered => NotificationUndelivered(_uuid)
              case Sent        => NotificationSent(_uuid)
              case Delivered   => NotificationDelivered(_uuid)
              case _           => NotificationPending(_uuid)
            }
          }
        ~> replyTo)
      case _ => Effect.unhandled
    }
  }

}

trait AllNotificationsBehavior extends NotificationBehavior[Notification] with AllNotificationsProvider {
  override val persistenceId = "Notification"
}

trait MockAllNotificationsBehavior extends AllNotificationsBehavior with MockAllNotificationsProvider {
  override val persistenceId = "MockNotification"
}

object AllNotificationsBehavior extends AllNotificationsBehavior

object MockAllNotificationsBehavior extends MockAllNotificationsBehavior