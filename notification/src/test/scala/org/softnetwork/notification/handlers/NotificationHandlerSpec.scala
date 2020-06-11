package org.softnetwork.notification.handlers

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.jdbc.util.PersistenceTypedActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.notification.message._
import org.softnetwork.notification.model.{From, Mail}
import org.softnetwork.notification.peristence.typed.MockAllNotificationsBehavior

/**
  * Created by smanciot on 14/04/2020.
  */
class NotificationHandlerSpec extends MockNotificationHandler with AnyWordSpecLike
  with PersistenceTypedActorTestKit {

  val from = ("pierre.durand@gmail.com", Some("Pierre Durand"))
  val to = Seq("nobody@gmail.com")
  val subject = "Sujet"
  val message = "message"

  private[this] def _mail(uuid: String) =
    Mail.defaultInstance.withUuid(uuid).withFrom(From(from, None)).withTo(to).withSubject(subject).withMessage(message)

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      MockAllNotificationsBehavior.init(context.system)
      Behaviors.empty
    }
  }

  implicit lazy val system = typedSystem()

  "NotificationTypedHandler" must {

    "add notification" in {
      val uuid = "add"
      this ? (
        uuid,
        new AddNotification(_mail(uuid))
      ) match {
        case n: NotificationAdded => n.uuid shouldBe uuid
        case _                    => fail()
      }
    }

    "remove notification" in {
      val uuid = "remove"
      this ? (
        uuid,
        new AddNotification(_mail(uuid))
      ) match {
        case n: NotificationAdded =>
          n.uuid shouldBe uuid
          this ? (
            uuid,
            new RemoveNotification(uuid)
          ) match {
            case _: NotificationRemoved.type =>
            case _ => fail()
          }
        case _ => fail()
      }
    }

    "send notification" in {
      val uuid = "send"
      this ? (
        uuid,
        new SendNotification(_mail(uuid))
      ) match {
        case n: NotificationSent => n.uuid shouldBe uuid
        case _                   => fail()
      }
    }

    "resend notification" in {
      val uuid = "resend"
      this ? (
        uuid,
        new SendNotification(_mail(uuid))
      ) match {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (
            uuid,
            new ResendNotification(uuid)
          ) match {
            case n: NotificationSent => n.uuid shouldBe uuid
            case _                   => fail()
          }
          this ? ("fake", new ResendNotification(uuid)) match {
            case NotificationNotFound =>
            case _                    => fail()
          }
        case _                    => fail()
      }
    }

    "retrieve notification status" in {
      val uuid = "status"
      this ? (
        uuid,
        new SendNotification(_mail(uuid))
      ) match {
        case n: NotificationSent =>
          n.uuid shouldBe uuid
          this ? (
            uuid,
            new GetNotificationStatus(uuid)
          ) match {
            case n: NotificationSent => n.uuid shouldBe uuid
            case _                   => fail()
          }
        case _                    => fail()
      }
    }
  }
}
