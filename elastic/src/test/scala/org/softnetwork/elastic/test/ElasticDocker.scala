package org.softnetwork.elastic.test

import java.net.{ServerSocket, URI}
import java.util.UUID

import com.sksamuel.elastic4s.http.bulk.BulkHandlers
import com.sksamuel.elastic4s.http.cat.CatHandlers
import com.sksamuel.elastic4s.http.cluster.ClusterHandlers
import com.sksamuel.elastic4s.http.delete.DeleteHandlers
import com.sksamuel.elastic4s.http.explain.ExplainHandlers
import com.sksamuel.elastic4s.http.get.GetHandlers
import com.sksamuel.elastic4s.http.index.admin.{IndexAdminHandlers, RefreshIndexResponse}
import com.sksamuel.elastic4s.http.index.alias.IndexAliasHandlers
import com.sksamuel.elastic4s.http.index.mappings.MappingHandlers
import com.sksamuel.elastic4s.http.index.{IndexHandlers, IndexTemplateHandlers}
import com.sksamuel.elastic4s.http.locks.LocksHandlers
import com.sksamuel.elastic4s.http.nodes.NodesHandlers
import com.sksamuel.elastic4s.http.search.template.SearchTemplateHandlers
import com.sksamuel.elastic4s.http.search.{SearchHandlers, SearchScrollHandlers}
import com.sksamuel.elastic4s.http.task.TaskHandlers
import com.sksamuel.elastic4s.http.termvectors.TermVectorHandlers
import com.sksamuel.elastic4s.http.update.UpdateHandlers
import com.sksamuel.elastic4s.http.validate.ValidateHandlers
import com.sksamuel.elastic4s.http.{ElasticClient => Elastic4sClient, ElasticDsl}
import com.sksamuel.elastic4s.{ElasticApi, ElasticsearchClientUri, IndexAndTypes, Indexes}
import com.typesafe.scalalogging.StrictLogging
import com.whisk.docker.DockerReadyChecker.LogLineContains
import com.whisk.docker.impl.dockerjava.DockerKitDockerJava
import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.{DockerContainer, DockerKit, LogLineReceiver}
import org.softnetwork.elastic.client.ElasticCredentials
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException

import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

/**
  * Created by smanciot on 28/06/2018.
  */
trait ElasticDockerTestKit
  extends TestSuite
    with Matchers
    with ElasticApi
    with BulkHandlers
    with CatHandlers
    with ClusterHandlers
    with DeleteHandlers
    with ExplainHandlers
    with GetHandlers
    with IndexHandlers
    with IndexAdminHandlers
    with IndexAliasHandlers
    with IndexTemplateHandlers
    with LocksHandlers
    with MappingHandlers
    with NodesHandlers
    with SearchHandlers
    with SearchTemplateHandlers
    with SearchScrollHandlers
    with UpdateHandlers
    with TaskHandlers
    with TermVectorHandlers
    with ValidateHandlers
    with DockerKitDockerJava
    with ElasticDocker
    with DockerTestKit {

  val esCredentials = {
    sys.props += ("ELASTIC_CREDENTIALS_URL" -> elasticURL)
    ElasticCredentials(elasticURL, "", "")
  }

  implicit lazy val client: Elastic4sClient = Elastic4sClient(ElasticsearchClientUri(host, httpPort))

  override def beforeAll(): Unit = {
    super.beforeAll
    client.execute {
      createIndexTemplate("all_templates", "*").settings(Map("number_of_shards" -> 1, "number_of_replicas" -> 0))
    }.await
  }

  override def afterAll(): Unit = {
    client.close()
    super.afterAll
  }

  import scala.concurrent.duration._

  // Rewriting methods from IndexMatchers in elastic4s with the Elastic4sClient
  def haveCount(expectedCount: Int)(implicit client: Elastic4sClient,
                                    timeout: FiniteDuration = 10.seconds): Matcher[String] = new Matcher[String] {

    def apply(left: String): MatchResult = {
      val count = client.execute(search(left).size(0)).await(timeout).result.totalHits
      MatchResult(
        count == expectedCount,
        s"Index $left had count $count but expected $expectedCount",
        s"Index $left had document count $expectedCount"
      )
    }
  }

  def containDoc(expectedId: String)(implicit client: Elastic4sClient,
                                     timeout: FiniteDuration = 10.seconds): Matcher[String] =
    new Matcher[String] {

      override def apply(left: String): MatchResult = {
        val exists = client.execute(get(expectedId).from(left)).await(timeout).result.exists
        MatchResult(
          exists,
          s"Index $left did not contain expected document $expectedId",
          s"Index $left contained document $expectedId"
        )
      }
    }

  def beCreated(implicit client: Elastic4sClient, timeout: FiniteDuration = 10.seconds): Matcher[String] =
    new Matcher[String] {

      override def apply(left: String): MatchResult = {
        val exists = client.execute(indexExists(left)).await(timeout).result.isExists
        MatchResult(
          exists,
          s"Index $left did not exist",
          s"Index $left exists"
        )
      }
    }

  def beEmpty(implicit client: Elastic4sClient, timeout: FiniteDuration = 10.seconds): Matcher[String] =
    new Matcher[String] {

      override def apply(left: String): MatchResult = {
        val count = client.execute(search(left).size(0)).await(timeout).result.totalHits
        MatchResult(
          count == 0,
          s"Index $left was not empty",
          s"Index $left was empty"
        )
      }
    }

  // Copy/paste methos HttpElasticSugar as it is not available yet

  // refresh all indexes
  def refreshAll(): RefreshIndexResponse = refresh(Indexes.All)

  // refreshes all specified indexes
  def refresh(indexes: Indexes): RefreshIndexResponse = {
    client
      .execute {
        refreshIndex(indexes)
      }
      .await
      .result
  }

  def blockUntilGreen(): Unit = {
    blockUntil("Expected cluster to have green status") { () =>
      client
        .execute {
          clusterHealth()
        }
        .await
        .result
        .status
        .toUpperCase == "GREEN"
    }
  }

  def blockUntil(explain: String)(predicate: () => Boolean): Unit = {

    var backoff = 0
    var done    = false

    while (backoff <= 16 && !done) {
      if (backoff > 0) Thread.sleep(200 * backoff)
      backoff = backoff + 1
      try {
        done = predicate()
      } catch {
        case e: Throwable =>
          logger.warn("problem while testing predicate", e)
      }
    }

    require(done, s"Failed waiting on: $explain")
  }

  def ensureIndexExists(index: String): Unit = {
    try {
      client.execute {
        createIndex(index)
      }.await
    } catch {
      case _: ResourceAlreadyExistsException => // Ok, ignore.
      case _: RemoteTransportException       => // Ok, ignore.
    }
  }

  def doesIndexExists(name: String): Boolean = {
    client
      .execute {
        indexExists(name)
      }
      .await
      .result
      .isExists
  }

  def doesAliasExists(name: String): Boolean = {
    client
      .execute {
        aliasExists(name)
      }
      .await
      .result
      .isExists
  }

  def deleteIndex(name: String): Unit = {
    if (doesIndexExists(name)) {
      client.execute {
        ElasticDsl.deleteIndex(name)
      }.await
    }
  }

  def truncateIndex(index: String): Unit = {
    deleteIndex(index)
    ensureIndexExists(index)
    blockUntilEmpty(index)
  }

  def blockUntilDocumentExists(id: String, index: String, `type`: String): Unit = {
    blockUntil(s"Expected to find document $id") { () =>
      client
        .execute {
          get(id).from(index / `type`)
        }
        .await
        .result
        .exists
    }
  }

  def blockUntilCount(expected: Long, index: String): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      val result = client.execute {
        search(index).matchAllQuery().size(0)
      }.await
      expected <= result.result.totalHits
    }
  }

  def blockUntilCount(expected: Long, indexAndTypes: IndexAndTypes): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      val result = client.execute {
        searchWithType(indexAndTypes).matchAllQuery().size(0)
      }.await
      expected <= result.result.totalHits
    }
  }

  /**
    * Will block until the given index and optional types have at least the given number of documents.
    */
  def blockUntilCount(expected: Long, index: String, types: String*): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      val result = client.execute {
        searchWithType(index / types).matchAllQuery().size(0)
      }.await
      expected <= result.result.totalHits
    }
  }

  def blockUntilExactCount(expected: Long, index: String, types: String*): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      expected == client
        .execute {
          searchWithType(index / types).size(0)
        }
        .await
        .result
        .totalHits
    }
  }

  def blockUntilEmpty(index: String): Unit = {
    blockUntil(s"Expected empty index $index") { () =>
      client
        .execute {
          search(Indexes(index)).size(0)
        }
        .await
        .result
        .totalHits == 0
    }
  }

  def blockUntilIndexExists(index: String): Unit = {
    blockUntil(s"Expected exists index $index") { () ⇒
      doesIndexExists(index)
    }
  }

  def blockUntilIndexNotExists(index: String): Unit = {
    blockUntil(s"Expected not exists index $index") { () ⇒
      !doesIndexExists(index)
    }
  }

  def blockUntilAliasExists(alias: String): Unit = {
    blockUntil(s"Expected exists alias $alias") { () ⇒
      doesAliasExists(alias)
    }
  }

  def blockUntilDocumentHasVersion(index: String, `type`: String, id: String, version: Long): Unit = {
    blockUntil(s"Expected document $id to have version $version") { () =>
      client
        .execute {
          get(id).from(index / `type`)
        }
        .await
        .result
        .version == version
    }
  }

}

trait ElasticDocker extends DockerKit with StrictLogging {

  import ElasticDocker._

  private def log(s: String) = logger.warn(s)

  val host: String = sys.env
    .get("DOCKER_HOST")
    .flatMap { uri =>
      if (uri.startsWith("unix://")) {
        None
      } else Some(new URI(uri).getHost)
    }
    .getOrElse("127.0.0.1")

  val httpPort: Int = dynamicPort
  val elasticURL    = s"http://$host:$httpPort"

  val clusterName: String = s"test-${UUID.randomUUID()}"

  override val StartContainersTimeout: FiniteDuration = 2.minutes

  val elasticsearchVersion = "5.6.16"

  val elasticsearchContainer: DockerContainer = DockerContainer(s"docker.elastic.co/elasticsearch/elasticsearch:$elasticsearchVersion")
    .withEnv(
      "http.host=0.0.0.0",
      "xpack.graph.enabled=false",
      "xpack.ml.enabled=false",
      "xpack.monitoring.enabled=false",
      "xpack.security.enabled=false",
      "xpack.watcher.enabled=false",
      s"cluster.name=$clusterName",
//      "script.inline=true",
//      "script.stored=true",
      "discovery.type=single-node"
//      "script.max_compilations_per_minute=60"
    )
    .withNetworkMode("bridge")
    .withPorts(9200 -> Some(httpPort))
    .withReadyChecker(
      LogLineContains("started")
    )
    .withLogLineReceiver(LogLineReceiver(withErr = true, log))

  override val dockerContainers: List[DockerContainer] =
    elasticsearchContainer :: super.dockerContainers
}

object ElasticDocker {

  def dynamicPort: Int = {
    val socket = new ServerSocket(0)
    val port   = socket.getLocalPort
    socket.close()
    port
  }
}
