package org.softnetwork.kafka.api

import java.util.Properties

import cakesolutions.kafka.testkit.KafkaServer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import kafka.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import org.softnetwork.kafka.utils.{Topic, TopicAdmin}

import scala.concurrent.duration._

/**
  * Created by smanciot on 18/03/2018.
  */
trait KafkaSpec extends Suite with TopicAdmin with BeforeAndAfterAll with Matchers with Eventually with StrictLogging {

  val kafkaConfigMap: Map[String, String] = Map(
    "num.partitions" -> "1",
    "offsets.topic.num.partitions" -> "1",
    "offsets.topic.replication.factor" -> "1",
    "log.cleaner.enable" -> "false"
  )

  val kafkaServer = new KafkaServer(kafkaConfig = kafkaConfigMap)

  lazy val broker: String = s"localhost:${kafkaServer.kafkaPort}"

  lazy val zookeeper: String = s"localhost:${kafkaServer.zookeeperPort}"

  def blockUntil(explain: String, maxTries: Int = 16, sleep: Int = 2000)(predicate: () => Boolean): Unit = {

    var tries = 0
    var done  = false

    while (tries <= maxTries && !done) {
      if (tries > 0) Thread.sleep(sleep * tries)
      tries = tries + 1
      try {
        done = predicate()
      } catch {
        case e: Throwable =>
          logger.warn(s"problem while testing predicate ${e.getMessage}")
      }
    }

    require(done, s"Failed waiting on: $explain")
  }

  lazy val defaultKafkaConfig = ConfigFactory.parseString(s"""
                                                                   |    kafka {
                                                                   |      topic-config.replication = 0
                                                                   |      topic-config.partitions = 1
                                                                   |      uri = "$broker"
                                                                   |      zookeeper = "$zookeeper"
                                                                   |      consumer {
                                                                   |        bootstrap.servers = "$broker"
                                                                   |      }
                                                                   |      producer {
                                                                   |        bootstrap.servers = "$broker"
                                                                   |      }
                                                                   |    }
                                                                   |    """.stripMargin)

  protected def waitForKafkaUp(maxTries: Int = 10, sleep: Int = 1000) = {
    val props = new Properties()
    props.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker)
    val client = AdminClient.create(props)
    blockUntil("kafka up", maxTries, sleep)(() => client.findAllBrokers().nonEmpty)
  }

  def createTopics(topics: String*): Unit = {
    val topicList: Seq[Topic] = topics.toList.map {
      Topic(_, 1, 1)
    }
    createTopics(zookeeper, topicList)

    val zTopics = listTopicNames(zookeeper)
    topics.foreach { t =>
      zTopics should contain(t)
    }
  }

  def createTopic(topic: String): Unit = {
    createTopic(zookeeper, Topic(topic, 1, 1))

    val zTopics = listTopicNames(zookeeper)
    zTopics should contain(topic)
  }

  def deleteTopic(topic: String): Unit = deleteTopic(zookeeper, topic)

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaServer.startup()
//    waitForKafkaUp()
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
         |  uri = "$broker"
         |  zookeeper = "$zookeeper"
         |}
        """.stripMargin

    ConfigFactory.parseString(config).withFallback(ConfigFactory.load())
  }
}
