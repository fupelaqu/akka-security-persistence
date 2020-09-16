package org.softnetwork.elastic.sql

import org.scalatest.{FlatSpec, Matchers}

/**
  *
  * Created by smanciot on 17/02/17.
  */
class SQLLiteralSpec extends FlatSpec with Matchers {

  "SQLLiteral" should "perform sql like" in {
    val l = SQLLiteral("%dummy%")
    l.like(Seq("dummy")) should === (true)
    l.like(Seq("aa dummy")) should === (true)
    l.like(Seq("dummy bbb")) should === (true)
    l.like(Seq("aaa dummy bbb")) should === (true)
    l.like(Seq("dummY")) should === (false)
  }
}
