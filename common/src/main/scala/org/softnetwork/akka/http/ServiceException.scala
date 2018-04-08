package org.softnetwork.akka.http

import akka.http.scaladsl.model.StatusCode

abstract class ServiceException(message: String, val code: StatusCode) extends Exception(message)
