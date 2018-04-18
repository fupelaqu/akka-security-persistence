package org.softnetwork.notification.handlers

import java.util.{Date, UUID}

import org.softnetwork.notification.config.Settings
import org.softnetwork.notification.config.Settings.PushConfig
import org.softnetwork.notification.model.NotificationStatus.NotificationStatus
import org.softnetwork.notification.model.{NotificationStatus, NotificationAck, Push}

/**
  * Created by smanciot on 14/04/2018.
  */
class PushProvider extends NotificationProvider[Push] {

  val pushConfig: PushConfig = Settings.config.get.push

  override def send(notification: Push): NotificationAck = throw new UnsupportedOperationException

  override def ack(uuid: String, currentStatus: NotificationStatus): NotificationAck =
    throw new UnsupportedOperationException
}

class MockPushProvider extends PushProvider{
  override def send(notification: Push): NotificationAck = NotificationAck(
    Some(UUID.randomUUID().toString),
    NotificationStatus.Sent,
    new Date()
  )
  override def ack(uuid: String, currentStatus: NotificationStatus): NotificationAck =
    NotificationAck(Some(uuid), currentStatus, new Date())

}