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

import com.typesafe.config.{Config, ConfigFactory}
import helpers.{BaseSpec, Constants}
import models.{ChargeReference, SubmissionResponse}
import models.declarations.State
import org.apache.pekko.pattern.CircuitBreaker
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.wordspec.AnyWordSpec
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.inject.*
import play.api.libs.ws.BodyWritable
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HODConnectorSpec extends BaseSpec with Constants {

  private trait Setup {
    val mockHttpClientV2: HttpClientV2     = mock(classOf[HttpClientV2])
    val mockRequestBuilder: RequestBuilder = mock(classOf[RequestBuilder])

    lazy val fakeApp: Application = new GuiceApplicationBuilder()
      .overrides(
        bind[HttpClientV2].toInstance(mockHttpClientV2),
        bind[RequestBuilder].toInstance(mockRequestBuilder)
      )
      .build()

    val connector: HODConnector = fakeApp.injector.instanceOf[HODConnector]

    when(mockRequestBuilder.withBody(any())(using any[BodyWritable[JsValue]], any(), any()))
      .thenReturn(mockRequestBuilder)

  }

  private trait CMASetup {
    val mockHttpClientV2: HttpClientV2     = mock(classOf[HttpClientV2])
    val mockRequestBuilder: RequestBuilder = mock(classOf[RequestBuilder])

    lazy val fakeApp: Application = new GuiceApplicationBuilder()
      .configure("feature.isUsingCMA" -> true)
      .overrides(
        bind[HttpClientV2].toInstance(mockHttpClientV2),
        bind[RequestBuilder].toInstance(mockRequestBuilder)
      )
      .build()

    val connector: HODConnector = fakeApp.injector.instanceOf[HODConnector]

    when(mockRequestBuilder.withBody(any())(using any[BodyWritable[JsValue]], any(), any()))
      .thenReturn(mockRequestBuilder)

  }

  "submit" should {
    "return a submitted response when a new declaration is submitted successfully" in new Setup {
      val response: SubmissionResponse = SubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(connector.submit(declaration, isAmendment = false)) shouldBe SubmissionResponse.Submitted
    }

    "return a submitted response when a new declaration is submitted successfully and CMA is enabled" in new CMASetup {
      val response: SubmissionResponse = SubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))

      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(connector.submit(declaration, isAmendment = false)) shouldBe SubmissionResponse.Submitted
    }

    "return a submitted response if an amended declaration is submitted" in new Setup {

      val response: SubmissionResponse = SubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(
        connector.submit(
          declaration.copy(amendCorrelationId = Some("amendCorrectionId"), amendData = Some(amendmentData)),
          isAmendment = true
        )
      ) shouldBe response
    }

    "return a submitted response if an amended declaration is submitted and CMA is enabled" in new CMASetup {

      val response: SubmissionResponse = SubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(
        connector.submit(
          declaration.copy(amendCorrelationId = Some("amendCorrectionId"), amendData = Some(amendmentData)),
          isAmendment = true
        )
      ) shouldBe response
    }

    "return an error if in the new declaration journey and the declaration data is empty" in new Setup {

      val response: SubmissionResponse = SubmissionResponse.Error

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(
        connector.submit(declaration.copy(journeyData = Json.obj()), isAmendment = false)
      ) shouldBe response
    }

    "return an error if in the new declaration journey with CMA enabled and the declaration data is empty" in new CMASetup {

      val response: SubmissionResponse = SubmissionResponse.Error

      val cma: Config = ConfigFactory.parseString("microservice.services.des-cma.enabled = true")

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(
        connector.submit(declaration.copy(journeyData = Json.obj()), isAmendment = false)
      ) shouldBe response
    }

    "return an error if in the amendment journey and amendment data is empty" in new Setup {

      val response: SubmissionResponse = SubmissionResponse.Error

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(
        connector
          .submit(declaration.copy(amendCorrelationId = Some("amendCorrectionId")), isAmendment = true)
      ) shouldBe response
    }

    "return an error if in the amendment journey and amendment data is empty and CMA is enabled" in new CMASetup {

      val response: SubmissionResponse = SubmissionResponse.Error

      val cma: Config = ConfigFactory.parseString("microservice.services.des-cma.enabled = true")

      when(mockRequestBuilder.execute(using any[HttpReads[SubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(
        connector
          .submit(
            declaration.copy(amendCorrelationId = Some("amendCorrectionId"), amendData = None),
            isAmendment = true
          )
      ) shouldBe response
    }

  }
}
