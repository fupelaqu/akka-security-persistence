package org.softnetwork.elastic.persistence.typed

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior

import akka.persistence.jdbc.util.PersistenceTypedActorTestKit

import org.scalatest.wordspec.AnyWordSpecLike
import org.softnetwork.akka.message.CommandWrapper

import org.softnetwork.elastic.client.MockElasticApi

import org.softnetwork.elastic.message._
import org.softnetwork.akka.model.Sample

import scala.language.implicitConversions

/**
  * Created by smanciot on 11/04/2020.
  */
class ElasticBehaviorSpec extends AnyWordSpecLike with PersistenceTypedActorTestKit {

  override def guardian(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] {context =>
      SampleBehavior.init(context.system)
      Behaviors.empty
    }
  }

  import SampleBehavior._

  "ElasticTypedActor" must {

    "CreateDocument" in {
      val probe = createTestProbe[ElasticResult]()
// FIXME      val ref = entityRefFor(TypeKey, "create")
//      ref ! ElasticEntityCommandWrapper(CreateDocument(Sample("create")), probe.ref)
//      probe.expectMessageType[DocumentCreated]
//      probe.expectMessage(DocumentCreated("create"))
    }

    "UpdateDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "update")
      ref ! CommandWrapper(UpdateDocument(Sample("update")), probe.ref)
      probe.expectMessage(DocumentUpdated("update"))
    }

    "DeleteDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "delete")
      ref ! CommandWrapper(CreateDocument(Sample("delete")), probe.ref)
      probe.expectMessage(DocumentCreated("delete"))
      ref ! CommandWrapper(DeleteDocument("delete"), probe.ref)
      probe.expectMessage(DocumentDeleted)
    }

    "LoadDocument" in {
      val probe = createTestProbe[ElasticResult]()
      val ref = entityRefFor(TypeKey, "load")
      val sample = Sample("load")
      ref ! CommandWrapper(CreateDocument(sample), probe.ref)
      probe.expectMessage(DocumentCreated("load"))
      ref ! CommandWrapper(LoadDocument("load"), probe.ref)
      probe.expectMessage(DocumentLoaded(sample))
    }

  }

}

object SampleBehavior extends ElasticBehavior[Sample]
  with MockElasticApi[Sample] {

  override val persistenceId = "Sample"

  val manifestWrapper = ManifestW()

}
