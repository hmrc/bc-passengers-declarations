package connectors

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import utils.WireMockHelper
import play.api.test.Helpers._
import com.github.tomakehurst.wiremock.client.WireMock._
import models.ChargeReference
import models.declarations.Declaration
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.test.Injecting
import uk.gov.hmrc.http.HeaderCarrier

class HODConnectorSpec extends FreeSpec with MustMatchers with OneAppPerSuite with WireMockHelper
  with ScalaFutures with IntegrationPatience with Injecting {

  override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port" -> server.port()
      )
      .build()
  }

  private lazy val connector: HODConnector = inject[HODConnector]

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "hod connector" - {

    "must call the HOD" in {

      val declaration = Declaration(ChargeReference(123), Json.obj())

      server.stubFor(
        post(urlEqualTo("/declarations/passengerdeclaration/v1"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .withHeader(ACCEPT, matching(ContentTypes.JSON))
          .withHeader("X-Correlation-ID", matching("^.+$"))
          .withHeader("X-Forwarded-Host", matching("MDTP"))
          .withHeader(DATE, matching("^.+$"))
          .withRequestBody(equalTo(Json.stringify(Json.toJson(declaration))))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      whenReady(connector.submit(declaration)) {
        _.status mustBe NO_CONTENT
      }
    }
  }
}
