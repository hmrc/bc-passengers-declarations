package connectors

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import utils.WireMockHelper
import play.api.test.Helpers._
import com.github.tomakehurst.wiremock.client.WireMock._
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

      val request = Json.obj(
        "foo" -> "bar"
      )

      server.stubFor(
        post(urlEqualTo("/declarations/passengerdeclaration/v1"))
          .withRequestBody(equalTo(Json.stringify(request)))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      whenReady(connector.submit(request)) {
        _.status mustBe NO_CONTENT
      }
    }
  }
}
