import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.routes
import models.ChargeReference
import models.declarations.Declaration
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Minutes, Seconds, Span}
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.ws.EmptyBody
import play.api.test.Helpers.{NO_CONTENT, running}
import play.api.test.{FakeRequest, WsTestClient}
import suite.MongoSuite
import utils.WireMockHelper
import workers.DeclarationSubmissionWorker
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class JourneySpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar with WireMockHelper {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(2, Minutes)),
    interval = scaled(Span(10, Seconds))
  )

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.des.port" -> server.port(),
      "workers.declaration-submission-worker.interval" -> "5 seconds",
      "locks.ttl" -> 1
    )

  "a paid declaration must be submitted to des" in {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
      .willReturn(aResponse().withStatus(NO_CONTENT))
    )

    database.flatMap(_.drop()).futureValue

    val app = builder.build()

    running(app) {

      started(app).futureValue

      val response = route(app, FakeRequest(POST, routes.DeclarationController.submit().url)
        .withJsonBody(Json.obj("simpleDeclarationRequest" -> Json.obj("foo" -> "bar"))).withHeaders("X-Correlation-ID" -> correlationId)).value

      val chargeReference: String = (contentAsJson(response) \ "simpleDeclarationRequest" \ "requestDetail" \ "declarationHeader" \ "chargeReference").as[JsString].value

      route(app, FakeRequest(POST, routes.DeclarationController.update(ChargeReference(chargeReference).get).url)).value.futureValue

      val submissionWorker = app.injector.instanceOf[DeclarationSubmissionWorker]

      submissionWorker.tap.pull.futureValue

      server.verify(1, postRequestedFor(urlEqualTo("/declarations/passengerdeclaration/v1")))
    }
  }
}
