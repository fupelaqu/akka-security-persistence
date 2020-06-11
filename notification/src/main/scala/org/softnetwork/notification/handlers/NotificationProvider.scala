package org.softnetwork.notification.handlers

import java.util.{Date, UUID}

import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.notification.model.{NotificationStatusResult, NotificationStatus, NotificationAck, Notification}

import org.softnetwork.notification.model._

/**
  * Created by smanciot on 14/04/2018.
  */
trait NotificationProvider[T<:Notification] {
  def send(notification: T): NotificationAck
  def ack(notification: T): NotificationAck = NotificationAck(notification.ackUuid, notification.results, new Date())
}

trait MockNotificationProvider[T<:Notification] extends NotificationProvider[T] with StrictLogging {

  override def send(notification: T): NotificationAck = {
    logger.info(s"\r\n${notification.message}")
    NotificationAck(
      Some(UUID.randomUUID().toString),
      notification.to.map((recipient) => NotificationStatusResult(recipient, NotificationStatus.Sent, None)),
      new Date()
    )
  }

}

trait AllNotificationsProvider extends NotificationProvider[Notification] {
  override def send(notification: Notification): NotificationAck =
    notification match {
      case mail: Mail => MailProvider.send(mail)
      case push: Push => PushProvider.send(push)
      case sms: SMS => SMSModeProvider.send(sms)
      case other => NotificationAck(None, Seq.empty, new Date())
    }

  override def ack(notification: Notification): NotificationAck =
    notification match {
      case mail: Mail => MailProvider.ack(mail)
      case push: Push => PushProvider.ack(push)
      case sms: SMS => SMSModeProvider.ack(sms)
      case other => NotificationAck(notification.ackUuid, notification.results, new Date())
    }
}

trait MockAllNotificationsProvider extends NotificationProvider[Notification] with StrictLogging {
  override def send(notification: Notification): NotificationAck = {
    notification match {
      case mail: Mail => MockMailProvider.send(mail)
      case push: Push => MockPushProvider.send(push)
      case sms: SMS => MockSMSProvider.send(sms)
      case other => NotificationAck(None, Seq.empty, new Date())
    }
  }
}
