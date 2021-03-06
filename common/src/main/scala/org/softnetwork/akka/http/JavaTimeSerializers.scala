package org.softnetwork.akka.http

import java.{time => jt}

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

import scala.util.Try

/**
  * @author Valentin Kasas
  */
object JavaTimeSerializers {
  def all = Seq(LocalDateTimeISOSerializer, LocalDateISOSerializer)
}

case object LocalDateTimeISOSerializer
  extends CustomSerializer[jt.LocalDateTime](format => {

    val isoFormat                             = jt.format.DateTimeFormatter.ISO_DATE_TIME
    def isValidDateTime(str: String): Boolean = Try(isoFormat.parse(str)).isSuccess

    (
      {
        case JString(value) if isValidDateTime(value) =>
          jt.LocalDateTime.parse(value, isoFormat)
      }, {
      case ldt: jt.LocalDateTime => JString(ldt.format(isoFormat))
    }
      )
  })

case object LocalDateISOSerializer
  extends CustomSerializer[jt.LocalDate](format => {

    val isoFormat                         = jt.format.DateTimeFormatter.ISO_DATE
    def isValidDate(str: String): Boolean = Try(isoFormat.parse(str)).isSuccess

    (
      {
        case JString(value) if isValidDate(value) =>
          jt.LocalDate.parse(value, isoFormat)
      }, {
      case ldt: jt.LocalDate => JString(ldt.format(isoFormat))
    }
      )
  })
