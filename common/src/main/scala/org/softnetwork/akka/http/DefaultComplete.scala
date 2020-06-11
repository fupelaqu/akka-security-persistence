package org.softnetwork.akka.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.Formats

import org.softnetwork.akka.serialization._

import scala.util.{Failure, Success, Try}

/**
  * @author Valentin Kasas
  */
trait DefaultComplete { this: Directives ⇒

  implicit def formats: Formats

  def handleCall[T](call: ⇒ T, handler: T ⇒ Route): Route = {
    import Json4sSupport._
    val start = System.currentTimeMillis()
    val res = Try(call) match {
      case Failure(t: ServiceException) ⇒
        t.printStackTrace()
        complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Failure(t: Throwable) ⇒
        t.printStackTrace()
        complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Success(s) ⇒ handler(s)
    }
//    val duration = System.currentTimeMillis() - start
    res
  }

  def handleComplete[T](call: Try[Try[T]], handler: T ⇒ Route): Route = {
    import Json4sSupport._
    val start = System.currentTimeMillis()
    val ret = call match {
      case Failure(t) ⇒
        t.printStackTrace()
        complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
      case Success(res) ⇒
        res match {
          case Success(id) ⇒ handler(id)
          case Failure(t: ServiceException) ⇒
            t.printStackTrace()
            complete(t.code -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
          case Failure(t) ⇒
            t.printStackTrace()
            complete(StatusCodes.InternalServerError -> Map('type -> t.getClass.getSimpleName, 'error -> t.toString))
        }
    }
//    val duration = System.currentTimeMillis() - start
    ret
  }
}
