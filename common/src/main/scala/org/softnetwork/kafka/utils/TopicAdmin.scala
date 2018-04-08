package org.softnetwork.kafka.utils

import java.util.Properties

import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.apache.kafka.common.security.JaasUtils

final case class Topic(name: String, replication: Int, partitions: Int, topicConfig: Properties = new Properties)

trait TopicAdmin {

  private def zkUtils(zkUrl: String) =
    ZkUtils(zkUrl, sessionTimeout = 30000, connectionTimeout = 30000, JaasUtils.isZkSecurityEnabled)

  def createTopics(zkUrl: String,
                   topics: Seq[Topic]): Seq[Topic] = {
    topics.foreach { topic =>
      createTopic(zkUtils(zkUrl), topic)
    }
    topics
  }

  def createTopic(zkUrl: String,
                  topic: Topic): Unit =
    createTopic(zkUtils(zkUrl), topic)

  def deleteTopic(zkUrl: String,
                  topic: String): Unit = {
    val utils = zkUtils(zkUrl)
    if (AdminUtils.topicExists(utils, topic)) {
      AdminUtils.deleteTopic(utils, topic)
    }
  }

  private def createTopic(zkUtils: ZkUtils, topic: Topic): Unit =
    if (!AdminUtils.topicExists(zkUtils, topic.name)) {
      AdminUtils.createTopic(zkUtils, topic.name, topic.partitions, topic.replication, topic.topicConfig)
    }

  def listTopicNames(zkUrl: String): Iterable[String] =
    AdminUtils.fetchAllTopicConfigs(zkUtils(zkUrl)).keys

}
