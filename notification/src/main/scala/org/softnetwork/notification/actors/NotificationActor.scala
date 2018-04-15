package org.softnetwork.notification.actors

import java.util.{UUID, Date}

import akka.actor.{Props, ActorLogging}
import akka.persistence.{RecoveryCompleted, SnapshotOffer, PersistentActor}
import org.softnetwork.akka.message.Event
import org.softnetwork.notification.handlers.{MockSMSProvider, SMSProvider, MockMailProvider, MailProvider}
import org.softnetwork.notification.message._
import org.softnetwork.notification.model._

/**
  * Created by smanciot on 07/04/2018.
  */
class NotificationActor extends PersistentActor with ActorLogging {

  val mailProvider: MailProvider = new MailProvider

  val smsProvider: SMSProvider = new SMSProvider

  var state = NotificationState()

  /** number of events received before generating a snapshot - should be configurable **/
  val snapshotInterval: Long = 1000

  override def persistenceId: String = "notification"

  def updateState(event: Event): Unit = {
    event match {
      case evt: NotificationRecordedEvent[Notification] =>
        import evt._
        state = state.copy(notifications = state.notifications.updated(uuid, notification))
      case _                                            =>
    }
  }

  override def receiveRecover: Receive = {
    case e: Event                                      => updateState(e)
    case SnapshotOffer(_, snapshot: NotificationState) => state = snapshot
    case RecoveryCompleted                             => log.info(s"AccountState has been recovered")
  }

  override def receiveCommand: Receive = {
    case cmd: SendNotification[Notification] =>
      import cmd._
      val uuid = UUID.randomUUID().toString
      val ack = notification match {
        case mail: Mail => mailProvider.send(mail)
        case sms: SMS   => smsProvider.send(sms)
        case _          => NotificationAck(None, NotificationStatus.Pending, new Date())
      }
      persist(
        NotificationRecordedEvent(
          uuid,
          notification
            .incNbTries()
            .copyWithAck(ack)
        )
      ) {event =>
        updateState(event)
        context.system.eventStream.publish(event)
        ack.status match {
          case NotificationStatus.Rejected  => sender() ! NotificationRejected(uuid)
          case NotificationStatus.Sent      => sender() ! NotificationSent(uuid)
          case NotificationStatus.Delivered => sender() ! NotificationDelivered(uuid)
          case _                            => sender() ! NotificationUndelivered(uuid)
        }
        performSnapshotIfRequired()
      }

    /** no handlers **/
    case _             => sender() ! new NotificationErrorMessage("UnknownCommand")
  }

  private def performSnapshotIfRequired(): Unit = {
    if (lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0)
      saveSnapshot(state)
  }
}

case class NotificationState(notifications: Map[String, Notification] = Map.empty, pendings: Seq[String] = Seq.empty)

object NotificationActor {
  def props(): Props = Props(new NotificationActor)
}

object MockNotificationActor {
  def props(): Props = Props(new NotificationActor {
    override val mailProvider: MailProvider = new MockMailProvider
    override val smsProvider: SMSProvider = new MockSMSProvider
  })
}
