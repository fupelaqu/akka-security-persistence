package org.softnetwork.notification.handlers

import java.io.{File => JFile}
import java.util.{Date, ArrayList => JArrayList}

import javapns.devices.implementations.basic.BasicDevice
import javapns.{Push => ApnsPush}
import javapns.notification.{PushedNotification, Payload, PushNotificationBigPayload}

import com.google.android.gcm.server.{Result, Sender, Notification, Message}
import com.google.android.gcm.server.Message.Builder

import org.softnetwork.notification.config.Settings
import org.softnetwork.notification.config.Settings.PushConfig
import org.softnetwork.notification.model.{BasicDevice => Device, _}

import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.collection.JavaConversions._

/**
  * Created by smanciot on 14/04/2018.
  */
trait PushProvider extends NotificationProvider[Push] {

  val maxDevices = 1000

  val pushConfig: PushConfig = Settings.config.get.push

  override def send(notification: Push): NotificationAck = {
    // split notification per platform
    val (android, ios) = notification.devices.partition(_.platform == Platform.ANDROID)

    import APNSPushProvider._
    import GCMPushProvider._

    // send notification to devices per platform
    NotificationAck(None, apns(notification, ios) ++ gcm(notification, android), new Date())
  }

  @tailrec
  private def apns(
                    payload: Payload,
                    devices: Seq[Device],
                    status: Seq[NotificationStatusResult] = Seq.empty
                  ): Seq[NotificationStatusResult] = {
    import APNSPushProvider._
    import pushConfig.apns._
    val nbDevices: Int = devices.length
    if(nbDevices > 0){
      val results = ApnsPush.payload(
        payload,
        _keystore(keystore.path),
        keystore.password,
        dryRun,
        new JArrayList[BasicDevice](
          asJavaCollection((
            if(nbDevices > maxDevices)
              devices.take(maxDevices)
            else
              devices
            ).map(deviceToApnsBasicDevice)
          )
        )
      ).map(pushedNotificationToNotificationStatusResult)
      if(nbDevices > maxDevices){
        apns(payload, devices.drop(maxDevices), status ++ results)
      }
      else{
        status ++ results
      }
    }
    else {
      status
    }
  }

  @tailrec
  private def gcm(
                   payload: Message,
                   devices: Seq[Device],
                   status: Seq[NotificationStatusResult] = Seq.empty
                 ): Seq[NotificationStatusResult] = {
    import GCMPushProvider._
    val nbDevices: Int = devices.length
    if(nbDevices > 0){
      val results = new Sender(
        pushConfig.gcm.apiKey
      )
        .sendNoRetry(
          payload,
          new JArrayList[String](
            asJavaCollection((
              if(nbDevices > maxDevices)
                devices.take(maxDevices)
              else
                devices
              ).map(_.regId)
            )
          )
        ).getResults.map(resultToNotificationStatusResult)
      if(nbDevices > maxDevices){
        gcm(payload, devices.drop(maxDevices), status ++ results)
      }
      else{
        status ++ results
      }
    }
    else{
      status
    }
  }

}

object GCMPushProvider {

  implicit def toGcmPayload(notification: Push): Message = {
    val payload = new Builder().notification(
      new Notification.Builder(null)
        .title(notification.subject)
        .body(notification.message)
        .badge(notification.badge)
        .sound(notification.sound.orNull)
        .build()
    ).addData("id", notification.id)
    payload.build()
  }

  implicit def resultToNotificationStatusResult(result: Result): NotificationStatusResult = {
    Option(result.getMessageId) match {
      case Some(_) =>
        NotificationStatusResult(
          result.getCanonicalRegistrationId, //TODO check it
          NotificationStatus.Sent,
          None
        )
      case _       =>
        NotificationStatusResult(
          result.getCanonicalRegistrationId,
          NotificationStatus.Rejected,
          Option(result.getErrorCodeName)
        )
    }
  }
}

object APNSPushProvider {

  implicit def toApnsPayload(notification: Push): Payload = {
    val payload = PushNotificationBigPayload.complex()
    payload.addCustomAlertTitle(notification.subject)
    payload.addCustomAlertBody(notification.message)
    if(notification.badge > 0){
      payload.addBadge(notification.badge)
    }
    notification.sound.foreach(payload.addSound)
    payload.addCustomDictionary("id", notification.id)
    payload
  }

  implicit def deviceToApnsBasicDevice(device: Device): BasicDevice = new BasicDevice(device.regId, true)

  implicit def pushedNotificationToNotificationStatusResult(result: PushedNotification): NotificationStatusResult = {
    val ex = Option(result.getException)
    val error =
      if(ex.isDefined){
        Some(s"${result.getDevice.getToken} -> ${ex.get.getMessage}")
      }
      else{
        None
      }
    NotificationStatusResult(
      result.getDevice.getToken,
      if (result.isSuccessful)
        NotificationStatus.Sent
      else
        NotificationStatus.Rejected,
      error
    )
  }

  def _keystore(path: String): Object = {
    if(new JFile(path).exists){
      path
    }
    else{
      getClass.getClassLoader.getResourceAsStream(path)
    }
  }
}

trait MockPushProvider extends PushProvider with MockNotificationProvider[Push]

object PushProvider extends PushProvider

object MockPushProvider extends MockPushProvider
