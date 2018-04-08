package org.softnetwork.kafka.config

import com.typesafe.config.Config
import configs.Configs

/**
  * @param uri the uri to connect to kafka brokers
  * @param zookeeper the uri to connect to zookeeper
  */
case class KafkaConfig(uri: Option[String],
                       zookeeper: String,
                       cleanUp: Boolean = false,
                       autoResetConfig: Option[String] = None
                      )

trait KProp {

  val conf: Config
  lazy val kafkaConfig: KafkaConfig = Configs[KafkaConfig]
    .get(conf, "kafka")
    .valueOrThrow(_ => new IllegalStateException("Could not find kafka stream configuration keys"))
}
