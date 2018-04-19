package org.softnetwork.notification.handlers

import org.softnetwork.notification.config.Settings
import org.softnetwork.notification.config.Settings.PushConfig
import org.softnetwork.notification.model.{NotificationAck, Push}

/**
  * Created by smanciot on 14/04/2018.
  */
class PushProvider extends NotificationProvider[Push] {

  val pushConfig: PushConfig = Settings.config.get.push

  override def send(notification: Push): NotificationAck = throw new UnsupportedOperationException

}

class MockPushProvider extends PushProvider with MockNotificationProvider[Push]
