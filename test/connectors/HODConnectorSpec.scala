package connectors

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import models.declarations.{Declaration, State}
import models.{ChargeReference, SubmissionResponse}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.Injecting
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

class HODConnectorSpec extends FreeSpec with MustMatchers with OneAppPerSuite with WireMockHelper
  with ScalaFutures with IntegrationPatience with Injecting {

  override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port"                          -> server.port(),
        "microservice.services.des.circuit-breaker.max-failures"  -> 1,
        "microservice.services.des.circuit-breaker.reset-timeout" -> "1 second"
      )
      .build()
  }

  private val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

  private lazy val declaration = Declaration(ChargeReference(123), State.PendingPayment, correlationId, Json.obj())

  private def stubCall: MappingBuilder =
    post(urlEqualTo("/declarations/passengerdeclaration/v1"))
      .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
      .withHeader(ACCEPT, matching(ContentTypes.JSON))
      .withHeader("X-Correlation-ID", matching("^.+$"))
      .withHeader("X-Forwarded-Host", matching("MDTP"))
      .withHeader(DATE, matching("^.+$"))
      .withRequestBody(equalTo(Json.stringify(Json.toJson(declaration))))

  private lazy val connector: HODConnector = inject[HODConnector]

  private implicit val hc: HeaderCarrier = HeaderCarrier()


  "hod connector" - {

    "must call the HOD" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(declaration).futureValue mustBe SubmissionResponse.Submitted
    }

    "must fall back to a SubmissionResponse.Error when the downstream call errors" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.submit(declaration).futureValue mustBe SubmissionResponse.Error
    }

    "must fail fast while the circuit breaker is open" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(declaration).futureValue mustBe SubmissionResponse.Error
      connector.submit(declaration).futureValue mustBe SubmissionResponse.Error

      Thread.sleep(2000)
      connector.submit(declaration).futureValue mustBe SubmissionResponse.Submitted
    }
  }
}
