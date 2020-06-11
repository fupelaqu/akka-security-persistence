package org.softnetwork.akka.model

import java.util.Date

import org.softnetwork._

/**
  * Created by smanciot on 12/04/2020.
  */
case class Sample(uuid: String, var createdDate: Date = now(), var lastUpdated: Date = now()) extends Timestamped
