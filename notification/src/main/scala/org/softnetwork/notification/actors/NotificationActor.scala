package org.softnetwork.notification.actors

import java.util.{UUID, Date}

import akka.actor.{Props, ActorLogging}
import akka.persistence.{RecoveryCompleted, SnapshotOffer, PersistentActor}
import org.softnetwork.akka.message.RecordEvent
import org.softnetwork.notification.handlers._
import org.softnetwork.notification.message._
import org.softnetwork.notification.model._

/**
  * Created by smanciot on 07/04/2018.
  */
trait NotificationActor[T <: Notification] extends PersistentActor with ActorLogging { self: NotificationProvider[T] =>

  var innerState = new NotificationState[T]()

  /** number of events received before generating a snapshot - should be configurable **/
  val snapshotInterval: Long = 1000

  private val provider: NotificationProvider[T] = this

  def updateState(event: RecordEvent[String, T]): Unit = {
    event match {
      case evt: NotificationRecordedEvent[T] =>
        import evt._
        innerState = innerState.copy(notifications = innerState.notifications.updated(key, state))
        state.deferred match {
          case Some(deferred) =>
            if(deferred.after(new Date()) || state.status == NotificationStatus.Pending){
              innerState = innerState.copy(pendings = innerState.pendings + key)
            }
            else{
              innerState = innerState.copy(pendings = innerState.pendings - key)
            }
          case _              =>
        }

      case evt: NotificationRemovedEvent[T]             =>
        import evt._
        innerState = innerState.copy(notifications = innerState.notifications - key)
        innerState = innerState.copy(pendings = innerState.pendings - key)

      case _                                            =>
    }
  }

  def generateUUID(notification: Notification): String = UUID.randomUUID().toString

  override def receiveRecover: Receive = {
    case e: RecordEvent[String, T]                        => updateState(e)
    case SnapshotOffer(_, snapshot: NotificationState[T]) => innerState = snapshot
    case RecoveryCompleted                                => log.info(s"ActorState $persistenceId has been recovered")
  }

  override def receiveCommand: Receive = {

    case cmd: AddNotification[T] =>
      import cmd._
      val uuid = generateUUID(notification)
      persist(
        new NotificationRecordedEvent(
          uuid,
          notification
        )
      ) {event =>
        updateState(event)
        context.system.eventStream.publish(event)
        sender() ! NotificationAdded(uuid)
        performSnapshotIfRequired()
      }

    case cmd: RemoveNotification             =>
      import cmd._
      innerState.notifications.get(uuid) match {
        case Some(_) =>
          persist(
            new NotificationRemovedEvent[T](uuid)
          ) {event =>
            updateState(event)
            context.system.eventStream.publish(event)
            sender() ! NotificationRemoved
            performSnapshotIfRequired()
          }

        case _       => sender() ! NotificationNotFound
      }

    case cmd: SendNotification[T] =>
      import cmd._
      sendNotification(generateUUID(notification), notification)

    case cmd: ResendNotification =>
      import cmd._
      innerState.notifications.get(uuid) match {
        case Some(notification) =>
          import notification._
          if(maxTries > 0 && nbTries > maxTries){
            sender() ! NotificationMaxTriesReached
          }
          else{
            sendNotification(uuid, notification)
          }
        case _                  => sender() ! NotificationNotFound
      }

    case cmd: GetNotificationStatus =>
      import cmd._
      innerState.notifications.get(uuid) match {
        case Some(notification) =>
          import notification._
          import NotificationStatus._
          status match {
            case Delivered => sender() ! NotificationDelivered
            case Rejected  => sender() ! NotificationRejected
            case _         => ack(uuid, notification)
          }
        case _                  => sender() ! NotificationNotFound
      }

    /** no handlers **/
    case _             => sender() ! new NotificationErrorMessage("UnknownCommand")
  }

  def ack(uuid: String, notification: T) = {
    import notification._
    val ack: NotificationAck = ackUuid match {
      case Some(s) => provider.ack(s, results)
      case _       => NotificationAck(None, results, new Date())
    }
    persist(
      new NotificationRecordedEvent(
        uuid,
        notification.copyWithAck(ack).asInstanceOf[T]
      )
    ) {event =>
      updateState(event)
      context.system.eventStream.publish(event)
      import NotificationStatus._
      ack.status match {
        case Rejected  => sender() ! NotificationRejected(uuid)
        case Sent      => sender() ! NotificationSent(uuid)
        case Delivered => sender() ! NotificationDelivered(uuid)
        case _         => sender() ! NotificationUndelivered(uuid)
      }
      performSnapshotIfRequired()
    }
  }

  def sendNotification(uuid: String, notification: T) = {
    val maybeAck =
      notification.deferred match {
        case Some(deferred) if deferred.after(new Date()) => None
        case _                                            => Some(provider.send(notification))
      }
    val recordedEvent =
      maybeAck match {
        case Some(ack) =>
          new NotificationRecordedEvent(
            uuid,
            notification
              .incNbTries()
              .copyWithAck(ack).asInstanceOf[T]
          )
        case _       => new NotificationRecordedEvent(uuid, notification)
      }
    persist(recordedEvent) {event =>
      updateState(event)
      context.system.eventStream.publish(event)
      import NotificationStatus._
      event.state.status match {
        case Rejected  => sender() ! NotificationRejected(uuid)
        case Sent      => sender() ! NotificationSent(uuid)
        case Delivered => sender() ! NotificationDelivered(uuid)
        case _         => sender() ! NotificationUndelivered(uuid)
      }
      performSnapshotIfRequired()
    }
  }

  private def performSnapshotIfRequired(): Unit = {
    if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(innerState)
  }
}

case class NotificationState[T <: Notification](notifications: Map[String, T] = Map[String, T](), pendings: Set[String] = Set.empty)

object MailActor {
  def props(): Props = Props(new NotificationActor[Mail] with MailProvider{
    override def persistenceId: String = "mail"
  })
}

object MockMailActor {
  def props(): Props = Props(new NotificationActor[Mail] with MockMailProvider{
    override def persistenceId: String = "mock-mail"
  })
}

object SMSActor {
  def props(): Props = Props(new NotificationActor[SMS] with SMSProvider{
    override def persistenceId: String = "sms"
  })
}

object MockSMSActor {
  def props(): Props = Props(new NotificationActor[SMS] with MockSMSProvider{
    override def persistenceId: String = "mock-sms"
  })
}

object PushActor {
  def props(): Props = Props(new NotificationActor[Push] with PushProvider{
    override def persistenceId: String = "push"
  })
}

object MockPushActor {
  def props(): Props = Props(new NotificationActor[Push] with MockPushProvider{
    override def persistenceId: String = "mock-push"
  })
}
