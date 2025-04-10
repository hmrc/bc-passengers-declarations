package connectors

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, post, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import helpers.Constants
import models.SendEmailRequest
import models.declarations.Etmp
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.await
import play.api.test.Injecting
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper
import play.api.test.Helpers.defaultAwaitTimeout

import scala.concurrent.ExecutionContext.Implicits.global

class SendEmailConnectorISpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with Injecting
    with Constants {

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port"                          -> server.port(),
        "microservice.services.des.circuit-breaker.max-failures"  -> 1,
        "microservice.services.des.circuit-breaker.reset-timeout" -> "1 second"
      )
      .build()

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val verifiedEmail: Seq[String]             = Seq("verified@email.com")
//  val returnLinkURL = "testReturnLinkUrl"
  private val emailRequest: SendEmailRequest = SendEmailRequest(
    to = verifiedEmail,
    templateId = "passengers_payment_confirmation",
    parameters = Map(
      "TIMEOFARRIVAL"        -> "12:16 PM",
      "COSTGBP_2"            -> "12.50",
      "NAME_2"               -> "Fender Guitar",
      "NAME"                 -> "Surya Das",
      "DATEOFARRIVAL"        -> "10 November 2020",
      "COSTGBP_1"            -> "11.50",
      "COSTGBP_0"            -> "10.50",
      "REFERENCE"            -> "XAPR9876548240",
      "TRANSACTIONREFERENCE" -> "XAPR9876548240",
      "DATE"                 -> "9 November 2020 16:28",
      "NAME_1"               -> "All other electronic devices",
      "TOTAL"                -> "£2,000.99",
      "PLACEOFARRIVAL"       -> "Heathrow",
      "NAME_0"               -> "15 litres spirits"
    ),
    force = true
  )

  private val jsonEmailRequest: JsObject = Json.obj(
    "to"         -> verifiedEmail,
    "templateId" -> "passengers_payment_confirmation",
    "parameters" -> Json.obj(
      "TIMEOFARRIVAL"        -> "12:16 PM",
      "COSTGBP_2"            -> "12.50",
      "NAME_2"               -> "Fender Guitar",
      "NAME"                 -> "Surya Das",
      "DATEOFARRIVAL"        -> "10 November 2020",
      "COSTGBP_1"            -> "11.50",
      "COSTGBP_0"            -> "10.50",
      "REFERENCE"            -> "XAPR9876548240",
      "TRANSACTIONREFERENCE" -> "XAPR9876548240",
      "DATE"                 -> "9 November 2020 16:28",
      "NAME_1"               -> "All other electronic devices",
      "TOTAL"                -> "£2,000.99",
      "PLACEOFARRIVAL"       -> "Heathrow",
      "NAME_0"               -> "15 litres spirits"
    ),
    "force"      -> true
  )

  private def stub(data: JsObject = jsonEmailRequest): MappingBuilder =
    post(urlEqualTo("/localhost:8300/transactionengine/email"))
      .withRequestBody(equalTo(Json.stringify(jsonEmailRequest)))

  private lazy val connector: SendEmailConnector = inject[SendEmailConnector]

  "SendEmailConnector" when {
    "call the connector when an email request is submitted" in {

      server.stubFor(
        stub()
          .willReturn(aResponse().withStatus(ACCEPTED))
      )

      await(connector.requestEmail(emailRequest)) shouldBe true
    }

    "fall back to a EmailErrorResponse when the downstream call errors in sending email" in {

      server.stubFor(
        stub()
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(503))
      )

      await(connector.requestEmail(emailRequest)) shouldBe false
    }

    "fall back to a EmailErrorResponse when result is a Throwable" in {

      server.stubFor(
        stub()
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      await(connector.requestEmail(emailRequest)) shouldBe false
    }
  }
}
