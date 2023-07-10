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

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import models.SubmissionResponse
import models.declarations.Etmp
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.api.test.Injecting
import util.Constants
import utils.WireMockHelper

class HODConnectorSpec
    extends AnyFreeSpec
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

  private def stubCall(data: JsObject = declarationData): MappingBuilder =
    post(urlEqualTo("/declarations/passengerdeclaration/v1"))
      .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
      .withHeader(ACCEPT, matching(ContentTypes.JSON))
      .withHeader("X-Correlation-ID", matching(correlationId))
      .withHeader("Authorization", matching("Bearer changeme"))
      .withHeader("X-Forwarded-Host", matching("MDTP"))
      .withHeader(DATE, matching("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"""))
      .withRequestBody(equalTo(Json.stringify(Json.toJsObject(data.as[Etmp]))))

  private lazy val connector: HODConnector = inject[HODConnector]

  "hod connector" - {

    "must call the HOD when declaration is submitted" in {

      server.stubFor(
        stubCall()
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(declaration, isAmendment = false).futureValue mustBe SubmissionResponse.Submitted
    }

    "must fall back to a SubmissionResponse.Error when the downstream call errors while submitting declaration" in {

      server.stubFor(
        stubCall()
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.submit(declaration, isAmendment = false).futureValue mustBe SubmissionResponse.Error
    }

    "must fail fast while the circuit breaker is open when declaration is submitted" in {

      server.stubFor(
        stubCall()
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(declaration, isAmendment = false).futureValue mustBe SubmissionResponse.Error
      connector.submit(declaration, isAmendment = false).futureValue mustBe SubmissionResponse.Error

      Thread.sleep(2000)
      connector.submit(declaration, isAmendment = false).futureValue mustBe SubmissionResponse.Submitted
    }

    "must call the HOD when amendment is submitted" in {

      server.stubFor(
        stubCall(amendmentData)
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(amendment, isAmendment = true).futureValue mustBe SubmissionResponse.Submitted
    }

    "must throw an exception when amendment is submitted but contains no correlation id" in {

      val amendmentWithNoAmendmentCorrelationId = amendment.copy(amendCorrelationId = None)

      val result = intercept[Exception] {
        connector.submit(amendmentWithNoAmendmentCorrelationId, isAmendment = true)
      }

      result.getMessage mustBe "AmendCorrelation Id is empty"
    }

    "must fall back to a SubmissionResponse.ParsingException when the declaration data is not complete" in {

      val missingDataDeclaration = declaration.copy(data = Json.obj())

      connector
        .submit(missingDataDeclaration, isAmendment = false)
        .futureValue mustBe SubmissionResponse.ParsingException
    }

    "must fall back to a SubmissionResponse.ParsingException when the amendment data is not complete" in {

      val missingAmendmentDataDeclaration = amendment.copy(amendData = Some(Json.obj()))

      connector
        .submit(missingAmendmentDataDeclaration, isAmendment = true)
        .futureValue mustBe SubmissionResponse.ParsingException
    }

    "must fall back to a SubmissionResponse.Error when the downstream call errors in amendments journey" in {

      server.stubFor(
        stubCall(amendmentData)
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.submit(amendment, isAmendment = true).futureValue mustBe SubmissionResponse.Error
    }

    "must fail fast while the circuit breaker is open in amendments journey" in {

      server.stubFor(
        stubCall(amendmentData)
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(amendment, isAmendment = true).futureValue mustBe SubmissionResponse.Error
      connector.submit(amendment, isAmendment = true).futureValue mustBe SubmissionResponse.Error

      Thread.sleep(2000)
      connector.submit(amendment, isAmendment = true).futureValue mustBe SubmissionResponse.Submitted
    }
  }
}
