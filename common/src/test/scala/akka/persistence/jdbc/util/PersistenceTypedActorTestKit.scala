package akka.persistence.jdbc.util

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior, ActorSystem}
import akka.actor.testkit.typed.scaladsl.{TestProbe, ActorTestKit}

import akka.cluster.MemberStatus
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import akka.cluster.typed.{Join, Cluster}

import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import com.typesafe.config.{ConfigFactory, ConfigParseOptions, ConfigSyntax}

import org.scalatest.time.Span

import org.softnetwork.akka.message.Command

import org.softnetwork.akka.persistence.typed.EntitySystemLocator

import scala.concurrent.duration._

/**
  * Created by smanciot on 04/01/2020.
  */
trait PersistenceTypedActorTestKit extends PostgresTestKit {

  def systemNameFrom(clazz: Class[_]) =
    clazz.getName
      .replace('.', '-')
      .replace('_', '-')
      .filter(_ != '$')

  val systemName: String = systemNameFrom(getClass)

  val akka = s"""
                |akka {
                |  stdout-loglevel = off // defaults to WARNING can be disabled with off. The stdout-loglevel is only in effect during system startup and shutdown
                |  log-dead-letters-during-shutdown = on
                |  loglevel = debug
                |  log-dead-letters = on
                |  log-config-on-start = off // Log the complete configuration at INFO level when the actor system is started
                |  loggers = ["akka.event.slf4j.Slf4jLogger"]
                |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
                |
                |  actor {
                |    provider = "cluster"
                |    debug {
                |      receive = on // log all messages sent to an actor if that actors receive method is a LoggingReceive
                |      autoreceive = off // log all special messages like Kill, PoisonPill etc sent to all actors
                |      lifecycle = off // log all actor lifecycle events of all actors
                |      fsm = off // enable logging of all events, transitioffs and timers of FSM Actors that extend LoggingFSM
                |      event-stream = off // enable logging of subscriptions (subscribe/unsubscribe) on the ActorSystem.eventStream
                |    }
                |  }
                |
                |  persistence {
                |    journal {
                |      plugin = "jdbc-journal"
                |      // Enable the line below to automatically start the journal when the actorsystem is started
                |      // auto-start-journals = ["jdbc-journal"]
                |    }
                |    snapshot-store {
                |      plugin = "jdbc-snapshot-store"
                |      // Enable the line below to automatically start the snapshot-store when the actorsystem is started
                |      // auto-start-snapshot-stores = ["jdbc-snapshot-store"]
                |    }
                |    read-journal {
                |      plugin = "jdbc-read-journal"
                |    }
                |  }
                |
                |  cluster {
                |   seed-nodes = []
                |  }
                |}
                |
                |akka-persistence-jdbc {
                |  shared-databases {
                |    $slick
                |  }
                |}
                |
                |jdbc-journal {
                |  use-shared-db = "slick"
                |}
                |
                |# the akka-persistence-snapshot-store in use
                |jdbc-snapshot-store {
                |  use-shared-db = "slick"
                |}
                |
                |# the akka-persistence-query provider in use
                |jdbc-read-journal {
                |
                |  refresh-interval = "10ms"
                |  max-buffer-size = "500"
                |
                |  use-shared-db = "slick"
                |}
                |
                |
                |""".stripMargin

  val config = ConfigFactory.parseString(akka, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF))
    .withFallback(ConfigFactory.load())

  val testKit = ActorTestKit(systemName, config)

  private[this] implicit lazy val system: ActorSystem[Nothing] = testKit.system

  def typedSystem() = system

  /**
    * `PatienceConfig` from [[akka.actor.testkit.typed.TestKitSettings#DefaultTimeout]]
    */
  implicit val patience: PatienceConfig =
    PatienceConfig(testKit.testKitSettings.DefaultTimeout.duration, Span(100, org.scalatest.time.Millis))

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializePersistence()
    initAndJoinCluster()
    EntitySystemLocator(system)
  }

  // FIXME use Akka's initializePlugins instead when released https://github.com/akka/akka/issues/28808
  private[this] def initializePersistence(): Unit = {
    val persistenceId = PersistenceId.ofUniqueId(s"persistenceInit-${UUID.randomUUID()}")
    val ref = spawn(
      EventSourcedBehavior[String, String, String](
        persistenceId,
        "",
        commandHandler = (_, _) => Effect.stop(),
        eventHandler = (_, _) => ""))
    ref ! "start"
    createTestProbe().expectTerminated(ref, 30.seconds)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  def guardian(): Behavior[Nothing]

  /**
    * init and join cluster
    */
  final def initAndJoinCluster() = {
    testKit.spawn[Nothing](guardian(), "guardian")
    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    // let the nodes join and become Up
    blockUntil("let the nodes join and become Up", 16, 2000)(() => Cluster(system).selfMember.status == MemberStatus.Up)
  }

  def createTestProbe[M](): TestProbe[M] = testKit.createTestProbe()

  def spawn[T](behavior: Behavior[T]): ActorRef[T] = testKit.spawn(behavior)

  def entityRefFor[R <: Command](typeKey: EntityTypeKey[R], entityId: String): EntityRef[R] =
    ClusterSharding(system).entityRefFor(typeKey, entityId)
}
