package org.softnetwork.notification.actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import org.softnetwork.notification.message._

/**
  * Created by smanciot on 05/05/2018.
  */
trait NotificationSupervisor extends Actor with ActorLogging{

  def mailProps: Props
  def mailName: String

  def smsProps: Props
  def smsName: String

  def pushProps: Props
  def pushName: String

  private[this] var mailRef: ActorRef = _
  private[this] var smsRef: ActorRef = _
  private[this] var pushRef: ActorRef = _

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    mailRef = context.system.actorOf(mailProps, mailName)
    smsRef = context.system.actorOf(smsProps, smsName)
    pushRef = context.system.actorOf(pushProps, pushName)
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
  }

  override def receive: Actor.Receive = {
    case cmd: MailCommand => mailRef.tell(cmd, sender())

    case cmd: SMSCommand  => smsRef.tell(cmd, sender())

    case cmd: PushCommand => pushRef.tell(cmd, sender())

    /** no handlers **/
    case _                => sender() ! NotificationUnknownCommand
  }
}

object NotificationSupervisor{
  def props(mail:String = "mail", sms:String = "sms", push:String = "push") = Props(new NotificationSupervisor {
    override lazy val mailProps: Props = MailActor.props()

    override lazy val smsProps: Props = SMSActor.props()

    override lazy val pushProps: Props = PushActor.props()

    override lazy val smsName: String = sms

    override lazy val mailName: String = mail

    override lazy val pushName: String = push
  })
}

object MockNotificationSupervisor{
  def props(mail:String = "mail", sms:String = "sms", push:String = "push") = Props(new NotificationSupervisor {
    override lazy val mailProps: Props = MockMailActor.props()

    override lazy val smsProps: Props = MockSMSActor.props()

    override lazy val pushProps: Props = MockPushActor.props()

    override lazy val smsName: String = sms

    override lazy val mailName: String = mail

    override lazy val pushName: String = push
  })
}