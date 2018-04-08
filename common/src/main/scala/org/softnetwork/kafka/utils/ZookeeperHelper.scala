package org.softnetwork.kafka.utils

import java.util

import org.apache.zookeeper.ZooKeeper

case class BrokerInfo(
                       jmx_port: Double,
                       timestamp: String,
                       endpoints: List[String],
                       host: String,
                       version: Double,
                       port: Int
                     ) {
  val uri = s"$host:$port"
}

trait ZookeeperHelper {

  /**
    * Get the list of brokers from zk's database
    * @param zookeeperUri - zk url
    * @return
    */
  def getBrokers(zookeeperUri: String): List[BrokerInfo] = {
    import org.json4s._
    import org.json4s.jackson.JsonMethods._
    implicit val formats = org.json4s.DefaultFormats

    import scala.collection.JavaConversions._

    val zk                     = new ZooKeeper(zookeeperUri, 10000, null)
    val ids: util.List[String] = zk.getChildren("/brokers/ids", false)
    ids.map { id =>
      val brokerInfo = new String(zk.getData("/brokers/ids/" + id, false, null))
      parse(brokerInfo, useBigDecimalForDouble = false).extract[BrokerInfo]
    }.toList
  }
}

