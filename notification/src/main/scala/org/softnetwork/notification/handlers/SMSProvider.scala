package org.softnetwork.notification.handlers

import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.notification.model.{NotificationAck, SMS}

/**
  * Created by smanciot on 14/04/2018.
  */
class SMSProvider extends NotificationProvider[SMS] with StrictLogging {
  def send(notification: SMS): NotificationAck = throw new UnsupportedOperationException
}

class MockSMSProvider extends SMSProvider with MockNotificationProvider[SMS]