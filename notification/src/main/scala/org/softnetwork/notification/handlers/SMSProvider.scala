package org.softnetwork.notification.handlers

import java.util.{UUID, Date}

import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.notification.model.{NotificationStatus, NotificationAck, SMS}

/**
  * Created by smanciot on 14/04/2018.
  */
class SMSProvider extends NotificationProvider[SMS] with StrictLogging {
  def send(notification: SMS): NotificationAck = throw new UnsupportedOperationException
}

class MockSMSProvider extends SMSProvider{
  override def send(notification: SMS): NotificationAck = NotificationAck(
    Some(UUID.randomUUID().toString),
    NotificationStatus.Sent,
    new Date()
  )
}