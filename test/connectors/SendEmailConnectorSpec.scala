/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package connectors

import helpers.BaseSpec
import models.SendEmailRequest
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import scala.concurrent.Future

class SendEmailConnectorSpec extends BaseSpec {

  trait Setup {
    val connector: SendEmailConnector = new SendEmailConnector {
      override val sendEmailURL = "testSendEmailURL"
      override val http: HttpClient = mockWSHttp

    }
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val verifiedEmail: Array[String] = Array("verified@email.com")
  val returnLinkURL = "testReturnLinkUrl"
  val emailRequest: SendEmailRequest = SendEmailRequest(
    to = verifiedEmail,
    templateId = "passengers_payment_confirmation",
    parameters = Map("TIMEOFARRIVAL" -> "12:16 PM",
                      "COSTGBP_2" -> "12.50",
                      "NAME_2" -> "Fender Guitar",
                      "NAME" -> "Surya Das",
                      "DATEOFARRIVAL" -> "10 November 2020",
                      "COSTGBP_1" -> "11.50",
                      "COSTGBP_0" -> "10.50",
                      "REFERENCE" -> "XAPR9876548240",
                      "TRANSACTIONREFERENCE" -> "XAPR9876548240",
                      "DATE" -> "9 November 2020 16:28",
                      "NAME_1" -> "All other electronic devices",
                      "TOTAL" -> "£2,000.99",
                      "PLACEOFARRIVAL" -> "Heathrow",
                      "NAME_0" -> "15 litres spirits"),
    force = true
  )

  "send Email" should {

    "Return a true when a request to send a new email is successful" in new Setup {
      mockHttpPOST(connector.sendEmailURL, HttpResponse(ACCEPTED))
      await(connector.requestEmail(emailRequest)) shouldBe true
    }

    "Fail the future when the service cannot be found" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when we send a bad request" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new BadRequestException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns an internal server error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new InternalServerException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns an upstream error" in new Setup {
      when(mockWSHttp.POST[JsValue, HttpResponse](Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new BadGatewayException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

  }
}
