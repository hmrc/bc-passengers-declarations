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
import models.declarations.{Declaration, Etmp, State}
import models.{ChargeReference, SubmissionResponse}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.api.test.Injecting
import utils.WireMockHelper
import org.scalatest.matchers.must.Matchers
import org.scalatest.freespec.AnyFreeSpec

class HODConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with WireMockHelper
    with ScalaFutures
    with IntegrationPatience
    with Injecting {

  override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port"                          -> server.port(),
        "microservice.services.des.circuit-breaker.max-failures"  -> 1,
        "microservice.services.des.circuit-breaker.reset-timeout" -> "1 second"
      )
      .build()

  private val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

  val journeyData: JsObject = Json.obj(
    "euCountryCheck"        -> "greatBritain",
    "arrivingNICheck"       -> true,
    "isUKResident"          -> false,
    "bringingOverAllowance" -> true
  )

  val data: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference"  -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"    -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails"     -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader"  -> Json.obj(
          "chargeReference"       -> "XJPR5768524625",
          "portOfEntry"           -> "LHR",
          "portOfEntryName"       -> "Heathrow Airport",
          "expectedDateOfArrival" -> "2018-05-31",
          "timeOfEntry"           -> "13:20",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate"),
          "travellingFrom"        -> "NON_EU Only",
          "onwardTravelGBNI"      -> "GB",
          "uccRelief"             -> false,
          "ukVATPaid"             -> false,
          "ukExcisePaid"          -> false
        ),
        "declarationTobacco" -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationAlcohol" -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            )
          )
        ),
        "declarationOther"   -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        ),
        "liabilityDetails"   -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        )
      )
    )
  )

  val amendData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference"     -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"       -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails"        -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader"     -> Json.obj(
          "chargeReference"       -> "XJPR5768524625",
          "portOfEntry"           -> "LHR",
          "portOfEntryName"       -> "Heathrow Airport",
          "expectedDateOfArrival" -> "2018-05-31",
          "timeOfEntry"           -> "13:20",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate"),
          "travellingFrom"        -> "NON_EU Only",
          "onwardTravelGBNI"      -> "GB",
          "uccRelief"             -> false,
          "ukVATPaid"             -> false,
          "ukExcisePaid"          -> false
        ),
        "declarationTobacco"    -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationAlcohol"    -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            )
          )
        ),
        "declarationOther"      -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        ),
        "liabilityDetails"      -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        ),
        "amendLiabilityDetails" -> Json.obj(
          "additionalExciseGBP"  -> "102.54",
          "additionalCustomsGBP" -> "534.89",
          "additionalVATGBP"     -> "725.03",
          "additionalTotalGBP"   -> "1362.46"
        )
      )
    )
  )

  private lazy val declaration = Declaration(
    ChargeReference(123),
    State.Paid,
    None,
    sentToEtmp = false,
    None,
    correlationId,
    None,
    journeyData,
    data
  )

  private lazy val amendment = Declaration(
    ChargeReference(123),
    State.Paid,
    None,
    sentToEtmp = false,
    None,
    correlationId,
    Some(correlationId),
    journeyData,
    data,
    Some(amendData)
  )

  private def stubCall: MappingBuilder =
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
        stubCall
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(declaration, false).futureValue mustBe SubmissionResponse.Submitted
    }

    "must fall back to a SubmissionResponse.Error when the downstream call errors while submitting declaration" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.submit(declaration, false).futureValue mustBe SubmissionResponse.Error
    }

    "must fail fast while the circuit breaker is open when declaration is submitted" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(declaration, false).futureValue mustBe SubmissionResponse.Error
      connector.submit(declaration, false).futureValue mustBe SubmissionResponse.Error

      Thread.sleep(2000)
      connector.submit(declaration, false).futureValue mustBe SubmissionResponse.Submitted
    }

    "must call the HOD when amendment is submitted" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(amendment, true).futureValue mustBe SubmissionResponse.Submitted
    }

    "must fall back to a SubmissionResponse.Error when the downstream call errors in amendments journey" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.submit(amendment, true).futureValue mustBe SubmissionResponse.Error
    }

    "must fail fast while the circuit breaker is open in amendments journey" in {

      server.stubFor(
        stubCall
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.submit(amendment, true).futureValue mustBe SubmissionResponse.Error
      connector.submit(amendment, true).futureValue mustBe SubmissionResponse.Error

      Thread.sleep(2000)
      connector.submit(amendment, true).futureValue mustBe SubmissionResponse.Submitted
    }
  }
}
