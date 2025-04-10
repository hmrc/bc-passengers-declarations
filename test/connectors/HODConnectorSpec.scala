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

import helpers.{BaseSpec, Constants}
import models.{ChargeReference, Service, SubmissionResponse}
import models.declarations.{Declaration, Etmp, State}
import org.apache.pekko.pattern.CircuitBreaker
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.playframework.cachecontrol.HttpDate
import org.scalatest.matchers.must.Matchers.mustBe
import play.api.{Application, Configuration}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.BodyWritable
import play.api.test.Helpers.{ACCEPT, CONTENT_TYPE, DATE, NO_CONTENT, await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import java.net.URL
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HODConnectorSpec extends BaseSpec with Constants {

  private trait Setup {
    implicit val mockHttpClientV2: HttpClientV2         = mock(classOf[HttpClientV2])
    implicit val mockRequestBuilder: RequestBuilder     = mock(classOf[RequestBuilder])
    implicit val mockRequestBuilderBody: RequestBuilder = mock(classOf[RequestBuilder])
    implicit val mockConfig: Configuration              = mock(classOf[Configuration])
    implicit val mockCircuitBreaker: CircuitBreaker     = mock(classOf[CircuitBreaker])
    private val baseUrl                                 = mockConfig.get[Service]("microservice.services.des")
    val declarationFullUrl                              = "http://localhost:9074/eclarations/passengerdeclaration/v1"
    private val bearerToken                             = "bearerToken1"

    implicit val hc: HeaderCarrier       = HeaderCarrier()
      .withExtraHeaders(
        HeaderNames.ACCEPT        -> ContentTypes.JSON,
        HeaderNames.DATE          -> HttpDate.now.toString,
        HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
        "X-Correlation-ID"        -> correlationId,
        "X-Forwarded-Host"        -> "MDTP"
      )
    implicit val connector: HODConnector = new HODConnector(mockHttpClientV2, mockConfig, mockCircuitBreaker)

  }

  "submit" should {
    "return a submitted response when a new declaration is submitted successfully" in new Setup {
      val response: SubmissionResponse = SubmissionResponse.Submitted
      when(
        mockHttpClientV2.post(ArgumentMatchers.eq(url"$declarationFullUrl"))(any())
      ).thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.withBody(any())(using any[BodyWritable[JsValue]], any(), any()))
        .thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))

      println(connector.call(declaration, false))

      connector.submit(
        declaration,
        isAmendment = false
      ) shouldBe response
    }

    "return a submitted response if an amended declaration is submitted" in new Setup {

      val response: SubmissionResponse = SubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))

      await(
        connector.submit(declaration.copy(amendCorrelationId = Some("amendCorrectionId")), isAmendment = true)
      ) shouldBe response
    }

//    "return an error if the declaration data is empty" in new Setup{
//
//      val response: SubmissionResponse = SubmissionResponse.Error
//
//      when(mockRequestBuilder.execute(using any[HttpReads[HttpResponse]], any()))
//        .thenReturn(Future(response))
//
//      when(
//        mockHttpClientV2.post(any())(any())
//      ).thenReturn(mockRequestBuilder)
//
//      await(connector.submit(Declaration(ChargeReference(0), State.SubmissionFailed, None, false, None, "", None, None, None, None, None), isAmendment = false)) shouldBe response
//    }

  }
}
