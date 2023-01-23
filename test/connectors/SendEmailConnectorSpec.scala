/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers._
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.{HttpClient, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendEmailConnectorSpec extends BaseSpec {

  private trait Setup {
    val mockHttpClient: HttpClient = mock[HttpClient]

    val connector: SendEmailConnector = new SendEmailConnector {
      override val sendEmailURL     = "testSendEmailURL"
      override val http: HttpClient = mockHttpClient

    }
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

  "send Email" should {

    "Return a true when a request to send a new email is successful" in new Setup {
      when(
        mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      ).thenReturn(Future.successful(HttpResponse.apply(ACCEPTED, "")))
      await(connector.requestEmail(emailRequest)) shouldBe true
    }

    "Fail the future when the service cannot be found" in new Setup {
      when(
        mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(new NotFoundException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when we send a bad request" in new Setup {
      when(
        mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(new BadRequestException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns an internal server error" in new Setup {
      when(
        mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(new InternalServerException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns an upstream error" in new Setup {
      when(
        mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(new BadGatewayException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

    "Fail the future when EVS returns another HTTP exception e.g 501" in new Setup {
      when(
        mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(new NotImplementedException("error")))

      intercept[EmailErrorResponse](await(connector.requestEmail(emailRequest)))
    }

  }
}
