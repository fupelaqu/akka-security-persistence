package org.softnetwork.security.model


import org.softnetwork.notification.model.Platform._

/**
  * @param regId - registration id
  * @param platform - device platform
  * @param applicationId - application id
  *                      may be useful to send notifications to all registered devices of a specific application
  */
case class DeviceRegistration(regId: String, platform: Platform, applicationId: Option[String])

