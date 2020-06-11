package org.softnetwork.security.config

/**
  * Created by smanciot on 02/09/2018.
  */
object Notifications {

  case class Config(
                     activation: String,
                     registration: String,
                     accountDisabled: String,
                     resetPassword: String,
                     passwordUpdated: String,
                     resetPasswordCode: Boolean
                   )

}
