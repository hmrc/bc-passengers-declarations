/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import helpers.BaseSpec
import models.SendEmailRequest
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.{mock, when}
import play.api.libs.json.JsValue
import play.api.libs.ws.BodyWritable
import play.api.test.Helpers.*
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendEmailConnectorSpec extends BaseSpec {

  private trait Setup {
    val mockHttpClientV2: HttpClientV2     = mock(classOf[HttpClientV2])
    val mockRequestBuilder: RequestBuilder = mock(classOf[RequestBuilder])

    val connector: SendEmailConnector = new SendEmailConnector {
      override val sendEmailURL       = "http://testSendEmailURL"
      override val http: HttpClientV2 = mockHttpClientV2

    }

    when(mockRequestBuilder.withBody(any())(using any[BodyWritable[JsValue]], any(), any()))
      .thenReturn(mockRequestBuilder)

  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val verifiedEmail: Seq[String]     = Seq("verified@email.com")
  val returnLinkURL                  = "testReturnLinkUrl"
  val emailRequest: SendEmailRequest = SendEmailRequest(
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

  "requestEmail" must {
    "return true when a request to send a new email is successful" in new Setup {
      val response: HttpResponse = HttpResponse(ACCEPTED, "")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(connector.requestEmail(emailRequest)) shouldBe true
    }

    "return false when a request to send a new email is successful but not ACCEPTED" in new Setup {
      val response: HttpResponse = HttpResponse(OK, "")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(connector.requestEmail(emailRequest)) shouldBe false
    }

    "fail the future when the service cannot be found" in new Setup {
      val response = new NotFoundException("error")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "fail the future when we send a bad request" in new Setup {
      val response = new BadRequestException("error")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "fail the future when EVS returns an internal server error" in new Setup {
      val response = new InternalServerException("error")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "fail the future when EVS returns an upstream error" in new Setup {
      val response = new BadGatewayException("error")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "fail the future when EVS returns another HTTP exception e.g 501" in new Setup {
      val response = new NotImplementedException("error")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "fail the future when EVS returns a Throwable exception" in new Setup {
      val response = new Throwable("error")

      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

  }
}
