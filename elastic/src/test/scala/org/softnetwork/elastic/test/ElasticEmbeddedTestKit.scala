package org.softnetwork.elastic.test

import java.util.UUID

import com.sksamuel.elastic4s.embedded.{InternalLocalNode, LocalNode}
import com.sksamuel.elastic4s.http.index.admin.RefreshIndexResponse
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticDsl}
import com.sksamuel.elastic4s.{IndexAndTypes, Indexes}
import org.elasticsearch.ResourceAlreadyExistsException
import org.elasticsearch.transport.RemoteTransportException

import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{BeforeAndAfterAll, TestSuite}
import org.scalatest.matchers.should.Matchers
import org.softnetwork.elastic.client.ElasticCredentials

/**
  * Created by smanciot on 28/06/2018.
  */
trait ElasticEmbeddedTestKit
  extends TestSuite
    with Matchers
    with BeforeAndAfterAll
    with ElasticDsl {

  val clusterName: String = s"test-${UUID.randomUUID()}"

  // spawn an embedded node for testing
  val localNode: InternalLocalNode = LocalNode(clusterName, s"/tmp/$clusterName")

  val httpPort: Int = localNode.port

  val host: String = localNode.host

  val elasticURL    = s"http://$host:$httpPort"

  val esCredentials = {
    sys.props += ("ELASTIC_CREDENTIALS_URL" -> elasticURL)
    ElasticCredentials(elasticURL, "", "")
  }

  // we create a client attached to the embedded node.
  implicit lazy val client: ElasticClient = localNode.client(shutdownNodeOnClose = true)

  override def beforeAll(): Unit = {
    super.beforeAll
    client.execute(
      createIndexTemplate("all_templates", "*").settings(Map("number_of_shards" -> 1, "number_of_replicas" -> 0))
    ).await
  }

  override def afterAll(): Unit = {
    client.close()
    super.afterAll
  }

  import scala.concurrent.duration._

  // Rewriting methods from IndexMatchers in elastic4s with the ElasticClient
  def haveCount(expectedCount: Int)(implicit client: ElasticClient,
                                    timeout: FiniteDuration = 10.seconds): Matcher[String] =
    (left: String) => {
      val count = client.execute(search(left).size(0)).await(timeout).result.totalHits
      MatchResult(
        count == expectedCount,
        s"Index $left had count $count but expected $expectedCount",
        s"Index $left had document count $expectedCount"
      )
    }

  def containDoc(expectedId: String)(implicit client: ElasticClient,
                                     timeout: FiniteDuration = 10.seconds): Matcher[String] =
    (left: String) => {
      val exists = client.execute(get(expectedId).from(left)).await(timeout).result.exists
      MatchResult(
        exists,
        s"Index $left did not contain expected document $expectedId",
        s"Index $left contained document $expectedId"
      )
    }

  def beCreated(implicit client: ElasticClient, timeout: FiniteDuration = 10.seconds): Matcher[String] =
    (left: String) => {
      val exists = client.execute(indexExists(left)).await(timeout).result.isExists
      MatchResult(
        exists,
        s"Index $left did not exist",
        s"Index $left exists"
      )
    }

  def beEmpty(implicit client: ElasticClient, timeout: FiniteDuration = 10.seconds): Matcher[String] =
    (left: String) => {
      val count = client.execute(search(left).size(0)).await(timeout).result.totalHits
      MatchResult(
        count == 0,
        s"Index $left was not empty",
        s"Index $left was empty"
      )
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
