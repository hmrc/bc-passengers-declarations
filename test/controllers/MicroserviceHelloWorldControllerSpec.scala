package controllers

import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import play.api.test.FakeRequest

class MicroserviceHelloWorldControllerSpec extends FreeSpec with MustMatchers with OptionValues with GuiceOneAppPerSuite {

  "GET / must" - {

    "return 200" in {

      val request = FakeRequest("GET", routes.MicroserviceHelloWorld.hello().url)

      val result = route(app, request).value

      status(result) mustEqual OK
    }
  }
}
