/*
 * Copyright 2022 HM Revenue & Customs
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

package util

import models.ChargeReference
import models.declarations.{Declaration, Etmp, State}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
class AuditingToolsSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with Injecting {

  private lazy val auditingTools: AuditingTools = inject[AuditingTools]

  "buildDeclarationDataEvent" - {
    "must produce the expected output when supplied a declaration" in {

      val chargeReference = ChargeReference(1234567890)
      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
      val journeyData = Json.obj(
          "foo" -> "bar"
        )
      val data =  Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "receiptDate" -> "2020-12-29T12:14:08Z",
            "acknowledgementReference" -> "XJPR57685246250",
            "requestParameters" -> Json.arr(
              Json.obj(
                "paramName" -> "REGIME",
                "paramValue" -> "PNGR"
              )
            )
          ),
          "requestDetail" -> Json.obj(
            "customerReference" -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
            "personalDetails" -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
            "contactDetails" -> Json.obj("emailAddress" -> "abc@gmail.com"),
            "declarationHeader" -> Json.obj("chargeReference" -> "XJPR5768524625", "portOfEntry" -> "LHR", "portOfEntryName" -> "Heathrow Airport", "expectedDateOfArrival" -> "2018-05-31", "timeOfEntry" -> "13:20", "messageTypes" -> Json.obj("messageType" -> "DeclarationCreate"), "travellingFrom" -> "NON_EU Only", "onwardTravelGBNI" -> "GB", "uccRelief" -> false, "ukVATPaid" -> false, "ukExcisePaid" -> false),
            "declarationTobacco" -> Json.obj(
              "totalExciseTobacco" -> "100.54",
              "totalCustomsTobacco" -> "192.94",
              "totalVATTobacco" -> "149.92",
              "declarationItemTobacco" -> Json.arr(
                Json.obj(
                  "commodityDescription" -> "Cigarettes",
                  "quantity" -> "250",
                  "goodsValue" -> "400.00",
                  "valueCurrency" -> "USD",
                  "valueCurrencyName" -> "USA dollars (USD)",
                  "originCountry" -> "US",
                  "originCountryName" -> "United States of America",
                  "exchangeRate" -> "1.20",
                  "exchangeRateDate" -> "2018-10-29",
                  "goodsValueGBP" -> "304.11",
                  "VATRESClaimed" -> false,
                  "exciseGBP" -> "74.00",
                  "customsGBP" -> "79.06",
                  "vatGBP" -> "91.43"
                )
              )
            ),
            "declarationAlcohol" -> Json.obj(
              "totalExciseAlcohol" -> "2.00",
              "totalCustomsAlcohol" -> "0.30",
              "totalVATAlcohol" -> "18.70",
              "declarationItemAlcohol" -> Json.arr(
                Json.obj(
                  "commodityDescription" -> "Cider",
                  "volume" -> "5",
                  "goodsValue" -> "120.00",
                  "valueCurrency" -> "USD",
                  "valueCurrencyName" -> "USA dollars (USD)",
                  "originCountry" -> "US",
                  "originCountryName" -> "United States of America",
                  "exchangeRate" -> "1.20",
                  "exchangeRateDate" -> "2018-10-29",
                  "goodsValueGBP" -> "91.23",
                  "VATRESClaimed" -> false,
                  "exciseGBP" -> "2.00",
                  "customsGBP" -> "0.30",
                  "vatGBP" -> "18.70"
                )
              )
            ),
            "declarationOther" -> Json.obj(
              "totalExciseOther" -> "0.00",
              "totalCustomsOther" -> "341.65",
              "totalVATOther" -> "556.41",
              "declarationItemOther" -> Json.arr(
                Json.obj(
                  "commodityDescription" -> "Television",
                  "quantity" -> "1",
                  "goodsValue" -> "1500.00",
                  "valueCurrency" -> "USD",
                  "valueCurrencyName" -> "USA dollars (USD)",
                  "originCountry" -> "US",
                  "originCountryName" -> "United States of America",
                  "exchangeRate" -> "1.20",
                  "exchangeRateDate" -> "2018-10-29",
                  "goodsValueGBP" -> "1140.42",
                  "VATRESClaimed" -> false,
                  "exciseGBP" -> "0.00",
                  "customsGBP" -> "159.65",
                  "vatGBP" -> "260.01"
                )
              )
            ),
            "liabilityDetails" -> Json.obj(
              "totalExciseGBP" -> "102.54",
              "totalCustomsGBP" -> "534.89",
              "totalVATGBP" -> "725.03",
              "grandTotalGBP" -> "1362.46"
            )
          )
        )
      )

      val declaration = Declaration(chargeReference, State.PendingPayment, None,sentToEtmp = false, None,correlationId, None, journeyData, data)

      val declarationEvent = auditingTools.buildDeclarationSubmittedDataEvent(declaration.data)

      declarationEvent.tags mustBe Map("transactionName" -> "passengerdeclarationsubmitted")
      declarationEvent.detail mustBe Json.toJsObject(data.as[Etmp])
      declarationEvent.auditSource mustBe "bc-passengers-declarations"
    }
  }
}
