package org.softnetwork.notification.model

import java.util.Date

/**
  * Created by smanciot on 14/04/2018.
  */
trait Notification {
  def from: (String, Option[String])
  def to: Seq[String]
  def subject: String
  def message: String
  def `type`: NotificationType.Value
  def maxTries: Int
  def nbTries: Int = 0
  def deferred: Option[Date] = None

  def ackUuid: Option[String] = None
  def status: NotificationStatus.Value = NotificationStatus.Pending
  def lastUpdated: Option[Date] = None

  def recipients: Seq[Notification.NotificationStatusPerRecipient] = Seq.empty

  def incNbTries(): Notification
  def copyWithAck(ack: NotificationAck): Notification
}

object Notification{
  type NotificationStatusPerRecipient = (String, NotificationStatus.Value)
}

case class NotificationAck(
  uuid: Option[String],
  recipients: Seq[Notification.NotificationStatusPerRecipient] = Seq.empty,
  date: Date = new Date()
){
  lazy val status: NotificationStatus.Value = {
    val distinct = recipients.map(_._2).distinct
    if(distinct.contains(NotificationStatus.Rejected)) {
      NotificationStatus.Rejected
    }
    else if(distinct.contains(NotificationStatus.Undelivered)){
      NotificationStatus.Undelivered
    }
    else if(distinct.contains(NotificationStatus.Pending)){
      NotificationStatus.Pending
    }
    else if(distinct.contains(NotificationStatus.Sent)){
      NotificationStatus.Sent
    }
    else{
      NotificationStatus.Delivered
    }
  }
}

object NotificationType extends Enumeration {
  type NotificationType = Value
  val Mail = Value(0, "Mail")
  val SMS = Value(1, "SMS")
  val Push = Value(2, "Push")
}

object NotificationStatus extends Enumeration {
  type NotificationStatus = Value
  val Pending = Value(0, "Pending")
  val Sent = Value(1, "Sent")
  val Delivered = Value(2, "Delivered")
  val Undelivered = Value(-1, "Undelivered")
  val Rejected = Value(-2, "Rejected")
}
