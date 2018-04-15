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

  def incNbTries(): Notification
  def copyWithAck(ack: NotificationAck): Notification
}

case class NotificationAck(uuid: Option[String], status: NotificationStatus.Value, date: Date = new Date())

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
