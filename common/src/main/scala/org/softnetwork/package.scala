package org

import java.math.BigInteger
import java.security.MessageDigest
import java.util.{Date, Calendar, UUID}

import org.softnetwork.akka.model.Timestamped

/**
  * Created by smanciot on 13/04/2020.
  */
package object softnetwork {

  trait ManifestWrapper[T]{
    protected case class ManifestW()(implicit val wrapped: Manifest[T])
    protected val manifestWrapper: ManifestW
  }

  def generateUUID(key: Option[String] = None): String =
    key match {
      case Some(clearText) => sha256(clearText)
      case _ => UUID.randomUUID().toString
    }

  def md5(clearText: String) = MessageDigest.getInstance("MD5").digest(clearText.getBytes).clone()

  def sha256(clearText: String) =
    String.format("%032x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(clearText.getBytes("UTF-8"))))

  def now(): Date = Calendar.getInstance().getTime

  def tomorrow(): Calendar = {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, 1)
    c
  }

  def getType[T <: Timestamped](implicit m: Manifest[T]): String = {
    m.runtimeClass.getSimpleName.toLowerCase
  }

}
