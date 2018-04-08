package org.softnetwork.session

import java.util.UUID

import com.softwaremill.session.{SessionConfig, SessionManager, SessionSerializer}
import org.softnetwork.session.config.Settings.Session._
import org.softnetwork.session.config.Settings.{Session => S}

import scala.collection.mutable
import scala.util.Try

case class Session(data: Session.Data = mutable.Map((CookieName, UUID.randomUUID.toString)), refreshable: Boolean = false) {

  private var dirty: Boolean = false

  def clear() = {
    val theId = id
    data.clear()
    this += CookieName -> theId
  }

  def isDirty = dirty

  def get(key: String): Option[Any] = data.get(key)

  def isEmpty: Boolean = data.isEmpty

  def contains(key: String): Boolean = data.contains(key)

  def -=(key: String): Session = synchronized {
    dirty = true
    data -= key
    this
  }

  def +=(kv: (String, Any)): Session = synchronized {
    dirty = true
    data += kv
    this
  }

  def apply(key: String): Any = data(key)

  val id = data(CookieName).asInstanceOf[String]
}

object Session {
  type Data = mutable.Map[String, Any]

  val sessionConfig = {
    SessionConfig.default(CookieSecret)
  }

  implicit val sessionManager = new SessionManager[Session](sessionConfig)

  def apply(uuid: String, refreshable: Boolean) = new Session(mutable.Map(CookieName -> uuid), refreshable)

  implicit def sessionSerializer: SessionSerializer[Session, String] = new SessionSerializer[Session, String] {
    override def serialize(session: Session): String = {
      val encoded = java.net.URLEncoder
        .encode(session.data.filterNot(_._1.contains(":")).map(d => d._1 + ":" + d._2).mkString("\u0000"), "UTF-8")
      Crypto.sign(encoded, CookieSecret) + "-" + encoded
    }

    override def deserialize(data: String): Try[Session] = {
      def urldecode(data: String) =
        mutable.Map[String, Any](
          java.net.URLDecoder
            .decode(data, "UTF-8")
            .split("\u0000")
            .map(_.split(":"))
            .map(p => p(0) -> p.drop(1).mkString(":")): _*
        )
      // Do not change this unless you understand the security issues behind timing attacks.
      // This method intentionally runs in constant time if the two strings have the same length.
      // If it didn't, it would be vulnerable to a timing attack.
      def safeEquals(a: String, b: String) = {
        if (a.length != b.length) false
        else {
          var equal = 0
          for (i <- Array.range(0, a.length)) {
            equal |= a(i) ^ b(i)
          }
          equal == 0
        }
      }

      Try {
        val splitted = data.split("-")
        val message  = splitted.tail.mkString("-")
        if (safeEquals(splitted(0), Crypto.sign(message, CookieSecret)))
          Session(data = urldecode(message))
        else
          throw new Exception("corrupted encrypted data")
      }
    }
  }
}
