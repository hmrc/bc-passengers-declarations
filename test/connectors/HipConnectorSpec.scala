/*
 * Copyright 2026 HM Revenue & Customs
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
import models.HipSubmissionResponse
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, verify, when}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.inject.*
import play.api.libs.ws.BodyWritable
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HipConnectorSpec extends BaseSpec with Constants {

  private trait Setup {
    val mockHttpClientV2: HttpClientV2     = mock(classOf[HttpClientV2])
    val mockRequestBuilder: RequestBuilder = mock(classOf[RequestBuilder])

    lazy val fakeApp: Application = new GuiceApplicationBuilder()
      .overrides(
        bind[HttpClientV2].toInstance(mockHttpClientV2),
        bind[RequestBuilder].toInstance(mockRequestBuilder)
      )
      .build()

    val connector: HipConnector = fakeApp.injector.instanceOf[HipConnector]

    when(mockRequestBuilder.withBody(any())(using any[BodyWritable[JsValue]], any(), any()))
      .thenReturn(mockRequestBuilder)
  }

  "submit" should {
    "return a submitted response when a new declaration is submitted successfully" in new Setup {
      val response: HipSubmissionResponse = HipSubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[HipSubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(
        mockHttpClientV2.post(any())(any())
      ).thenReturn(mockRequestBuilder)

      await(connector.submit(declaration, isAmendment = false)) shouldBe HipSubmissionResponse.Submitted
    }

    "forward travellingFrom unmapped (e.g. Great Britain), not throw, now that EPID1778 no longer enforces an enum" in new Setup {
      val response: HipSubmissionResponse = HipSubmissionResponse.Submitted
      when(mockRequestBuilder.execute(using any[HttpReads[HipSubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)

      val gbDeclaration = declaration.copy(data =
        declarationData deepMerge Json.obj(
          "simpleDeclarationRequest" -> Json.obj(
            "requestDetail" -> Json.obj(
              "declarationHeader" -> Json.obj("travellingFrom" -> "Great Britain")
            )
          )
        )
      )

      await(connector.submit(gbDeclaration, isAmendment = false)) shouldBe HipSubmissionResponse.Submitted

      val bodyCaptor: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(classOf[JsValue])
      verify(mockRequestBuilder).withBody(bodyCaptor.capture())(using any[BodyWritable[JsValue]], any(), any())
      (bodyCaptor.getValue \ "declarationHeader" \ "travellingFrom").as[String] shouldBe "Great Britain"
    }

    "not send an X-SAP-Number header, even when requestCommon.requestParameters has a SAP_NUMBER entry" in new Setup {
      // EPID1778 v1.1.0 (11-09-2026) removed X-SAP-Number entirely from the header contract -
      // it's no longer optional, it doesn't exist. This guards against ever re-adding it.
      val response: HipSubmissionResponse = HipSubmissionResponse.Submitted
      when(mockRequestBuilder.execute(using any[HttpReads[HipSubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)

      val declarationWithSapNumber = declaration.copy(data =
        declarationData deepMerge Json.obj(
          "simpleDeclarationRequest" -> Json.obj(
            "requestCommon" -> Json.obj(
              "requestParameters" -> Json.arr(
                Json.obj("paramName" -> "REGIME", "paramValue"     -> "PNGR"),
                Json.obj("paramName" -> "SAP_NUMBER", "paramValue" -> "XA00008000")
              )
            )
          )
        )
      )

      await(connector.submit(declarationWithSapNumber, isAmendment = false)) shouldBe HipSubmissionResponse.Submitted

      val hcCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      verify(mockHttpClientV2).post(any())(hcCaptor.capture())
      hcCaptor.getValue.extraHeaders.map(_._1) should not contain "X-SAP-Number"
    }

    "authorize requests with Basic auth built from the configured client-id and client-secret" in new Setup {
      val response: HipSubmissionResponse = HipSubmissionResponse.Submitted
      when(mockRequestBuilder.execute(using any[HttpReads[HipSubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)

      await(connector.submit(declaration, isAmendment = false)) shouldBe HipSubmissionResponse.Submitted

      val expectedToken                           = java.util.Base64.getEncoder.encodeToString("changeme:changeme".getBytes("UTF-8"))
      val hcCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      verify(mockHttpClientV2).post(any())(hcCaptor.capture())
      hcCaptor.getValue.extraHeaders should contain("Authorization" -> s"Basic $expectedToken")
    }

    "fall back to a freshly generated UUID when the stored correlationId is not a valid UUID" in new Setup {
      val response: HipSubmissionResponse = HipSubmissionResponse.Submitted
      when(mockRequestBuilder.execute(using any[HttpReads[HipSubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)

      await(
        connector.submit(declaration.copy(correlationId = "not-a-uuid"), isAmendment = false)
      ) shouldBe HipSubmissionResponse.Submitted

      val hcCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      verify(mockHttpClientV2).post(any())(hcCaptor.capture())
      val sentCorrelationId                       =
        hcCaptor.getValue.extraHeaders.collectFirst { case ("correlationid", value) => value }.getOrElse("")
      sentCorrelationId should not be "not-a-uuid"
      noException should be thrownBy java.util.UUID.fromString(sentCorrelationId)
    }

    "return a submitted response when an amendment is submitted successfully" in new Setup {
      val response: HipSubmissionResponse = HipSubmissionResponse.Submitted

      when(mockRequestBuilder.execute(using any[HttpReads[HipSubmissionResponse]], any()))
        .thenReturn(Future(response))
      when(mockHttpClientV2.post(any())(any())).thenReturn(mockRequestBuilder)

      await(connector.submit(amendment, isAmendment = true)) shouldBe HipSubmissionResponse.Submitted

      val hcCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
      verify(mockHttpClientV2).post(any())(hcCaptor.capture())
      hcCaptor.getValue.extraHeaders should contain("X-Message-Type" -> "DeclarationAmend")
    }

    "return a ParsingException, not throw, when the declaration data doesn't parse as an Etmp at all" in new Setup {
      await(
        connector.submit(declaration.copy(data = Json.obj()), isAmendment = false)
      ) shouldBe HipSubmissionResponse.ParsingException

      verify(mockHttpClientV2, org.mockito.Mockito.never()).post(any())(any())
    }

    "return a ParsingException, not throw, when EtmpHipTransformer fails to convert a non-numeric monetary value" in new Setup {
      val badDeclaration = declaration.copy(data =
        declarationData deepMerge Json.obj(
          "simpleDeclarationRequest" -> Json.obj(
            "requestDetail" -> Json.obj(
              "declarationTobacco" -> Json.obj(
                "declarationItemTobacco" -> Json.arr(
                  Json.obj("goodsValue" -> "not-a-number")
                )
              )
            )
          )
        )
      )

      await(connector.submit(badDeclaration, isAmendment = false)) shouldBe HipSubmissionResponse.ParsingException

      verify(mockHttpClientV2, org.mockito.Mockito.never()).post(any())(any())
    }
  }
}
