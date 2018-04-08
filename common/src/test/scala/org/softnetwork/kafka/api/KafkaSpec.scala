package org.softnetwork.kafka.api

import cakesolutions.kafka.testkit.KafkaServer
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import org.softnetwork.kafka.utils.{Topic, TopicAdmin}

import scala.concurrent.duration._

/**
  * Created by smanciot on 18/03/2018.
  */
trait KafkaSpec extends Suite with TopicAdmin with BeforeAndAfterAll with Matchers with Eventually {

  val kafkaConfigMap: Map[String, String] = Map(
    "num.partitions" -> "1",
    "offsets.topic.num.partitions" -> "1",
    "offsets.topic.replication.factor" -> "1",
    "log.cleaner.enable" -> "false"
  )

  val kafkaServer = new KafkaServer(kafkaConfig = kafkaConfigMap)

  lazy val zookeeperURL: String = s"localhost:${kafkaServer.zookeeperPort}"

  def kafkaURL = s"localhost:${kafkaServer.kafkaPort}"

  def createTopics(topics: String*): Unit = {
    val topicList: Seq[Topic] = topics.toList.map {
      Topic(_, 1, 1)
    }
    createTopics(zookeeperURL, topicList)

    val zTopics = listTopicNames(zookeeperURL)
    topics.foreach { t =>
      zTopics should contain(t)
    }
  }

  def createTopic(topic: String): Unit = {
    createTopic(zookeeperURL, Topic(topic, 1, 1))

    val zTopics = listTopicNames(zookeeperURL)
    zTopics should contain(topic)
  }

  def deleteTopic(topic: String): Unit = deleteTopic(zookeeperURL, topic)

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaServer.startup()
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    kafkaServer.close()
  }

  def produce[K, V](data: List[(K, V)],
                    topic: String,
                    keySerializer: Serializer[K],
                    valueSerializer: Serializer[V]): Unit = {
    kafkaServer.produce(topic,
      data.map { case (k, v) => new ProducerRecord(topic, k, v) },
      keySerializer,
      valueSerializer,
      producerConfig = Map(ProducerConfig.BATCH_SIZE_CONFIG -> "0"))
  }

  def consume[K, V](topic: String,
                    expectedNumOfRecords: Int,
                    keyDeserializer: Deserializer[K],
                    valueDeserializer: Deserializer[V]): Seq[(Option[K], V)] = {
    val t = 45.seconds
    val i = 5.seconds
    eventually(timeout(t), interval(i)) {
      kafkaServer.consume(
        topic,
        expectedNumOfRecords,
        i.toMillis,
        keyDeserializer,
        valueDeserializer
      )
    }
  }

  def init(topic: Topic): Config = {
    val config =
      s"""
         |kafka {
         |  topic {
         |    name: "${topic.name}"
         |  },
         |  uri = "$kafkaURL"
         |  zookeeper = "$zookeeperURL"
         |}
        """.stripMargin

    ConfigFactory.parseString(config).withFallback(ConfigFactory.load())
  }
}
