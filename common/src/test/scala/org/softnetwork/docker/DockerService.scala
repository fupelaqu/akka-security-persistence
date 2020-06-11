package org.softnetwork.docker

import com.spotify.docker.client.DefaultDockerClient
import com.typesafe.scalalogging.Logger
import com.whisk.docker._

import java.net.URI

import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.concurrent.Eventually
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration._

trait DockerService extends TestSuite
  with Matchers
  with DockerTestKit
  with Eventually {

  def log = Logger(LoggerFactory.getLogger(getClass.getName))

  def container: String

  def containerPorts: Seq[Int]

  def name: String = container.split(":").head

  def containerEnv = sys.env.filterKeys(_.startsWith(s"${name.toUpperCase}_"))

  import DockerService._

  def exposedPort(port: Int) = Some(
        containerEnv.getOrElse(s"${name.toUpperCase}_$port", dynamicPort().toString).toInt
      )

  lazy val exposedPorts = containerPorts.map{port => (port, exposedPort(port))}

  lazy val dockerContainer = DockerContainer(container, Some(name))
    .withPorts(exposedPorts:_*)
    .withEnv(
      containerEnv.map( t => s"${t._1}=${t._2}").toSeq:_*
    )

  def _container(): DockerContainer

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(
    DefaultDockerClient.fromEnv().build())

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
          log.warn(s"problem while testing predicate ${e.getMessage}")
      }
    }

    require(done, s"Failed waiting on: $explain")
  }

  protected def waitForContainerUp(maxTries: Int = 10, sleep: Int = 1000) = {
    val predicate: () => Boolean = () => Await.result(
      isContainerReady(_container()),
      sleep.milliseconds
    )
    blockUntil(s"container $container is up", maxTries, sleep)(predicate)
  }

}

object DockerService {

  import java.net.ServerSocket

  def dynamicPort(): Int = {
    val socket = new ServerSocket(0)
    val port   = socket.getLocalPort
    socket.close()
    port
  }

  def host(): String = sys.env
    .get("DOCKER_HOST")
    .flatMap { uri =>
      if (uri.startsWith("unix://")) {
        None
      } else {
          Some(new URI(uri).getHost)
      }
    }
    .getOrElse("127.0.0.1")

}
