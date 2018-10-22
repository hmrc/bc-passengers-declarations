package controllers

import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._

class DeclarationControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with OptionValues {

  "submit" - {

    "must return ACCEPTED when given a valid request" in {

      val requestBody = Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "requestDetail" -> Json.obj(
            "declarationHeader" -> Json.obj(
              "chargeReference" -> "1234567890asdf"
            )
          )
        )
      )

      val request = FakeRequest(POST, routes.DeclarationController.submit().url)
        .withJsonBody(requestBody)

      val result = route(app, request).value

      status(result) mustBe ACCEPTED
    }

    "must return BAD_REQUEST when given an invalid request" in {

      val request = FakeRequest(POST, routes.DeclarationController.submit().url)
        .withJsonBody(Json.obj())

      val result = route(app, request).value

      status(result) mustBe BAD_REQUEST
    }
  }
}
