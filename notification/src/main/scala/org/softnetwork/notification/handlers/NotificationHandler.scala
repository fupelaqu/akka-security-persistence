package org.softnetwork.notification.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.softnetwork.akka.handlers.EntityHandler
import org.softnetwork.akka.persistence.typed.CommandTypeKey
import org.softnetwork.notification.message._
import org.softnetwork.notification.model.Notification
import org.softnetwork.notification.peristence.typed._

import scala.reflect.ClassTag

/**
  * Created by smanciot on 14/04/2020.
  */

trait AllNotificationsTypeKey extends CommandTypeKey[NotificationCommand]{
  override def TypeKey(implicit tTag: ClassTag[NotificationCommand]): EntityTypeKey[NotificationCommand] =
    AllNotificationsBehavior.TypeKey
}

trait MockAllNotificationsTypeKey extends CommandTypeKey[NotificationCommand]{
  override def TypeKey(implicit tTag: ClassTag[NotificationCommand]): EntityTypeKey[NotificationCommand] =
    MockAllNotificationsBehavior.TypeKey
}

trait NotificationHandler extends EntityHandler[NotificationCommand, NotificationCommandResult]
  with AllNotificationsTypeKey {

  implicit def command2Request(command: NotificationCommand): Request = { (replyTo) =>
    NotificationCommandWrapper(command, replyTo)
  }

}

trait MockNotificationHandler extends NotificationHandler with MockAllNotificationsTypeKey

trait NotificationDao {_: NotificationHandler =>

  def sendNotification(notification: Notification)(implicit system: ActorSystem[_]): Boolean = {
    this !? new SendNotification(notification) match {
      case _: NotificationSent      => true
      case _: NotificationDelivered => true
      case _                        => false
    }
  }

  def resendNotification(id: String)(implicit system: ActorSystem[_]): Boolean = {
    this !? new ResendNotification(id) match {
      case _: NotificationSent      => true
      case _: NotificationDelivered => true
      case _                        => false
    }
  }

  def removeNotification(id: String)(implicit system: ActorSystem[_]): Boolean = {
    this !? new RemoveNotification(id) match {
      case _: NotificationSent      => true
      case _: NotificationDelivered => true
      case _                        => false
    }
  }

  def geNotificationStatus(id: String)(implicit system: ActorSystem[_]) = {
    this !? new GetNotificationStatus(id)
  }

}

trait MockNotificationDao extends NotificationDao with MockNotificationHandler

object NotificationDao extends NotificationDao with NotificationHandler

object MockNotificationDao extends MockNotificationDao