package org.softnetwork.akka

import java.time.Instant
import java.util.Date

import com.google.protobuf.timestamp.Timestamp
import org.json4s.JsonAST.{JInt, JString}
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.reflect.TypeInfo
import org.softnetwork.akka.http.JavaTimeSerializers

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scalapb.{GeneratedEnumCompanion, GeneratedEnum, TypeMapper}

/**
  * Created by smanciot on 14/05/2020.
  */
package object serialization {

  implicit val serialization = jackson.Serialization

  val commonFormats = Serialization.formats(NoTypeHints) ++
    JodaTimeSerializers.all ++
    JavaTypesSerializers.all ++
    JavaTimeSerializers.all

  implicit def map2String(data: Map[String, Any])(implicit formats: Formats): String = serialization.write(data)

  /**
    * required before migrating from kryo to another serialization format
    *
    * @param str - a string
    * @return an option
    */
  implicit def string2Option(str: String): Option[String] = if (str.trim.isEmpty) None else Some(str)

  /**
    * required before migrating from kryo to another serialization format
    *
    * @param opt - an option
    * @return a string
    */
  implicit def option2String(opt: Option[String]): String = if(opt.isDefined) opt.get else ""

  implicit def toTimestamp(date: Date): Timestamp = {
    val instant = Instant.ofEpochMilli(date.getTime)
    Timestamp(instant.getEpochSecond, instant.getNano)
  }

  implicit def toDate(timestamp: Timestamp): Date =
    new Date(
      Instant.ofEpochSecond(
        timestamp.seconds,
        timestamp.nanos
      ).toEpochMilli
    )

  implicit val tsTypeMapper: TypeMapper[Timestamp, Date] = new TypeMapper[Timestamp, Date] {
    override def toBase(custom: Date): Timestamp = custom

    override def toCustom(base: Timestamp): Date = base
  }

  case class GeneratedEnumSerializer[T <: GeneratedEnum: ClassTag](companion: GeneratedEnumCompanion[T])(
    implicit m: Manifest[T]) extends Serializer[T] {

    import JsonDSL._

    lazy val EnumerationClass = m.runtimeClass

    override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
      case (_@TypeInfo(EnumerationClass, _), json) if isValid(json) =>
        json match {
          case JString(value) => companion.fromName(value).get
          case JInt(value) => companion.fromValue(value.toInt)
          case value => throw new MappingException(s"Can't convert $value to $EnumerationClass")
        }
    }

    override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case i: T if companion.values.exists(t => t.value == i.value) => i.name
    }

    private[this] def isValid(json: JValue) = json match {
      case JString(value) if companion.fromName(value).isDefined => true
      case JInt(value) if value.isValidInt && companion.values.exists(t => t.value == value.toInt) => true
      case _ => false
    }

  }
}
