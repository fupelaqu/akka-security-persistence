package org.softnetwork.notification.peristence.typed

import java.util.Date

import akka.actor.typed.scaladsl.{ActorContext, TimerScheduler}
import akka.actor.typed.ActorRef

import akka.persistence.typed.scaladsl.Effect

import org.slf4j.Logger
import org.softnetwork.akka.model.Schedule

import org.softnetwork.akka.persistence.typed.EntityBehavior

import org.softnetwork.notification.handlers._

import org.softnetwork.notification.message._

import org.softnetwork.notification.model._

import scala.language.{implicitConversions, postfixOps}

/**
  * Created by smanciot on 13/04/2020.
  */
sealed trait NotificationBehavior[T <: Notification] extends EntityBehavior[
  NotificationCommand, T, NotificationEvent, NotificationCommandResult] { self: NotificationProvider[T] =>

  private[this] val provider: NotificationProvider[T] = this

  private[this] val notificationTimerKey: String = "NotificationTimerKey"

  protected val delay = 60

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
          case Some(event) => Effect.persist(event).thenRun(_ => {
            schedulerDao.addSchedule(Schedule(persistenceId, entityId, notificationTimerKey, delay))
            NotificationAdded(entityId) ~> replyTo
          })
          case _ => Effect.unhandled
        }

      case cmd: RemoveNotification =>
        Effect.persist[NotificationEvent, Option[T]](
          NotificationRemovedEvent(
            entityId
          )
        ).thenRun(_ => {
          schedulerDao.removeSchedule(persistenceId, entityId, notificationTimerKey)
          NotificationRemoved ~> replyTo
        })//.thenStop()

      case cmd: SendNotification[T] =>
        sendNotification(entityId, cmd.notification, replyTo) match {
          case Some(event) =>
            Effect.persist(event)
              .thenRun(state => {
                schedulerDao.addSchedule(Schedule(persistenceId, entityId, notificationTimerKey, delay))
                import NotificationStatus._
                event.asInstanceOf[NotificationRecordedEvent[T]].notification.status match {
                  case Rejected    => NotificationRejected(entityId)
                  case Undelivered => NotificationUndelivered(entityId)
                  case Sent        => NotificationSent(entityId)
                  case Delivered   => NotificationDelivered(entityId)
                  case _           => NotificationPending(entityId)
                }
              }
                ~> replyTo)
          case _ => Effect.none
        }

      case cmd: ResendNotification => state match {
        case Some(s) =>
          import s._
          import NotificationStatus._
          status match {
            case Sent      => Effect.none.thenRun(_ => NotificationSent(entityId) ~> replyTo)
            case Delivered => Effect.none.thenRun(_ => NotificationDelivered(entityId) ~> replyTo)
            case _         =>
              sendNotification(entityId, s, replyTo) match {
                case Some(event) =>
                  Effect.persist(event)
                    .thenRun(state => {
                      schedulerDao.addSchedule(Schedule(persistenceId, entityId, notificationTimerKey, delay))
                      import NotificationStatus._
                      event.asInstanceOf[NotificationRecordedEvent[T]].notification.status match {
                        case Rejected    => NotificationRejected(entityId)
                        case Undelivered => NotificationUndelivered(entityId)
                        case Sent        => NotificationSent(entityId)
                        case Delivered   => NotificationDelivered(entityId)
                        case _           => NotificationPending(entityId)
                      }
                    }
                      ~> replyTo)
                case _ => Effect.none
              }
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

      case cmd: TriggerSchedule4Notification =>
        import cmd.schedule._
        if(key == notificationTimerKey){
          context.self ! NotificationTimeout
          Effect.none.thenRun(_ => Schedule4NotificationTriggered ~> replyTo)
        }
        else{
          Effect.none.thenRun(_ => Schedule4NotificationNotTriggered ~> replyTo)
        }

      case NotificationTimeout =>
        state match {
          case Some(s) =>
            import s._
            import NotificationStatus._
            status match {
              case Sent      => // the notification has been sent - the schedule should be removed
                Effect.none.thenRun(_ => schedulerDao.removeSchedule(persistenceId, entityId, notificationTimerKey))
              case Delivered => // the notification has been delivered - the schedule should be removed
                Effect.none.thenRun(_ => schedulerDao.removeSchedule(persistenceId, entityId, notificationTimerKey))
              case Pending   => // the notification is in pending status ...
                sendNotification(entityId, s, None) match {
                  case Some(event) =>
                    Effect.persist(event)
                      .thenRun(state =>
                        schedulerDao.addSchedule(
                          Schedule(persistenceId, entityId, notificationTimerKey, delay)
                        )
                      )
                  case _ => Effect.none
                }
              case _         => // the notification has been rejected or undelivered
                if(maxTries > 0 && nbTries >= maxTries){
                  Effect.none.thenRun(_ => schedulerDao.removeSchedule(persistenceId, entityId, notificationTimerKey))
                }
                else {
                  sendNotification(entityId, s, None) match {
                    case Some(event) =>
                      Effect.persist(event)
                        .thenRun(state =>
                          schedulerDao.addSchedule(
                            Schedule(persistenceId, entityId, notificationTimerKey, delay)
                          )
                        )
                    case _ => Effect.none
                  }
                }
            }
          case _ =>
            Effect.none.thenRun(_ => schedulerDao.removeSchedule(persistenceId, entityId, notificationTimerKey))
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
  ): Option[NotificationEvent] = {
    import notification._
    import NotificationStatus._
    val maybeSent = status match {
      case Sent        => None
      case Delivered   => None
      case Pending     =>
        notification.deferred match {
          case Some(deferred) if deferred.after(new Date()) => None
          case _ =>
            if(nbTries > 0) { // the notification has already been sent at least one time, waiting for an acknowledgement
              Some((provider.ack(notification), 0)) // FIXME acknowledgment must be properly implemented ...
            }
            else {
              Some((provider.send(notification), 1))
            }
        }
      case _ =>
        // Undelivered or Rejected
        if(maxTries > 0 && nbTries >= maxTries){
          None
        }
        else {
          Some((provider.send(notification), 1))
        }
    }
    notification match {
      case n: Mail =>
        Some(
          MailRecordedEvent(
            maybeSent match {
              case Some(ack) =>
                n.withNbTries(n.nbTries + ack._2).copyWithAck(ack._1).asInstanceOf[Mail]
              case _ => n
            }
          )
        )
      case n: SMS =>
        Some(
          SMSRecordedEvent(
            maybeSent match {
              case Some(ack) => n.withNbTries(n.nbTries + ack._2).copyWithAck(ack._1).asInstanceOf[SMS]
              case _ => n
            }
          )
        )
      case n: Push =>
        Some(
          PushRecordedEvent(
            maybeSent match {
              case Some(ack) => n.withNbTries(n.nbTries + ack._2).copyWithAck(ack._1).asInstanceOf[Push]
              case _ => n
            }
          )
        )
      case _ => None
    }
  }

}

trait AllNotificationsBehavior extends NotificationBehavior[Notification] with AllNotificationsProvider {
  override val persistenceId = "Notification"
}

trait MockAllNotificationsBehavior extends AllNotificationsBehavior with MockAllNotificationsProvider {
  override val persistenceId = "MockNotification"
  override val delay = 0
}

object AllNotificationsBehavior extends AllNotificationsBehavior

object MockAllNotificationsBehavior extends MockAllNotificationsBehavior
