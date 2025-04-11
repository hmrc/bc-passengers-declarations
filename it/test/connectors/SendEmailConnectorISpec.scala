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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, matching, post, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import helpers.Constants
import models.SendEmailRequest
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.Status.{ACCEPTED, BAD_REQUEST}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{ACCEPT, await, defaultAwaitTimeout}
import play.api.test.Injecting
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

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
        "microservice.services.email.sendEmailURL.port" -> server.port()
      )
      .build()

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val emailRequest: SendEmailRequest = SendEmailRequest(
    to = Seq(emailAddress),
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

  private lazy val connector: SendEmailConnectorImpl = inject[SendEmailConnectorImpl]

  "SendEmailConnector" when {
    "call the connector when an email request is submitted" in {

      server.stubFor(
        post(urlEqualTo("/transactionengine/email"))
          .willReturn(
            aResponse()
              .withStatus(ACCEPTED)
          )
      )

      await(connector.requestEmail(emailRequest)) shouldBe true
    }

    "fall back to a EmailErrorResponse when the downstream call errors in sending email" in {

      server.stubFor(
        post(urlEqualTo("/transactionengine/email"))
          .withHeader(ACCEPT, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
              .withStatus(BAD_REQUEST)
          )
      )

      await(connector.requestEmail(emailRequest)) shouldBe false
    }

    "fall back to a EmailErrorResponse when result is a Throwable" in {

      server.stubFor(
        post(urlEqualTo("/transactionengine/email"))
          .withHeader(ACCEPT, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withFault(Fault.RANDOM_DATA_THEN_CLOSE)
          )
      )

      await(connector.requestEmail(emailRequest.copy(to = Seq.empty))) shouldBe false
    }
  }
}
