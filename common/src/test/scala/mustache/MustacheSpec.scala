package mustache

import org.scalatest.{Matchers, WordSpec}
import mustache._

import scala.io.Source

/**
  * Created by smanciot on 08/04/2018.
  */
class MustacheSpec extends WordSpec with Matchers {

  "Mustache" must {
    "render template propertly" in {
      val template = new Mustache(
        Source.fromFile(
          Thread.currentThread().getContextClassLoader.getResource("template/hello.mustache").getPath
        )
      )
      template.render(Map("name"->"world")) shouldBe "Hello world !"
    }
  }
}
