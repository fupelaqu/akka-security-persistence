package org.softnetwork.kafka.utils

import org.softnetwork.kafka.config.KafkaConfig
import org.apache.kafka.clients.consumer.OffsetResetStrategy

object KafkaUtils extends ZookeeperHelper {

  /**
    *
    * @param appId       - application id
    * @param kafkaConfig - kafka config
    * @return
    */
  def createProducerConfig(appId: String, kafkaConfig: KafkaConfig): Map[String, String] = {
    val kafkaUri = kafkaConfig.uri.getOrElse(brokersFromZookeeper(kafkaConfig.zookeeper))
    Map(
      "kafka.app.id" -> appId,
      "kafka.uri" -> kafkaUri,
      "kafka.zookeeper" -> kafkaConfig.zookeeper,
      "bootstrap.servers" -> kafkaUri
    )
  }

  /**
    *
    * @param appId       - application id
    * @param kafkaConfig - kafka config
    * @return
    */
  def createConsumerConfig(appId: String, kafkaConfig: KafkaConfig): Map[String, String] = {
    val kafkaUri = kafkaConfig.uri.getOrElse(brokersFromZookeeper(kafkaConfig.zookeeper))
    Map(
      "group.id" → appId,
      "enable.auto.commit" → "false",
      "max.poll.records" → "1",
      "auto.offset.reset" → OffsetResetStrategy.LATEST.toString.toLowerCase,
      "bootstrap.servers" -> kafkaUri
    )
  }

  def brokersFromZookeeper(zkURI: String): String = {
    val brokers = getBrokers(zkURI)
    brokers.map(_.uri).mkString(",")
  }

}
