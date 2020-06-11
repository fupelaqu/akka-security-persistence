package org.softnetwork.notification

import org.softnetwork.akka.serialization._
import org.softnetwork.notification.model.{MailType, Platform, NotificationStatus, NotificationType}

/**
  * Created by smanciot on 21/05/2020.
  */
package object serialization {

  val notificationFormats =
    commonFormats ++
      Seq(
        new GeneratedEnumSerializer(NotificationType.enumCompanion),
        new GeneratedEnumSerializer(NotificationStatus.enumCompanion),
        new GeneratedEnumSerializer(Platform.enumCompanion),
        new GeneratedEnumSerializer(MailType.enumCompanion)
      )

  implicit def formats = notificationFormats

}
