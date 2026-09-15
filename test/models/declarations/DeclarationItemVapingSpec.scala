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

package models.declarations

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class DeclarationItemVapingSpec extends AnyWordSpec with Matchers {

  "DeclarationItemVaping" should {

    "serialize to JSON" in {
      val declarationItemVaping = DeclarationItemVaping(
        commodityDescription = Some("Vape liquid"),
        volume = Some("50"),
        goodsValue = Some("40"),
        valueCurrency = Some("USD"),
        originCountry = Some("US"),
        exchangeRate = Some("1.2"),
        exchangeRateDate = Some("2026-09-01"),
        goodsValueGBP = Some("30.41"),
        VATRESClaimed = Some(false),
        exciseGBP = Some("12"),
        customsGBP = Some("1.5"),
        vatGBP = Some("9.4"),
        ukVATPaid = Some(false),
        ukExcisePaid = Some(false),
        euCustomsRelief = Some(false),
        madeIn = Some("US")
      )
      val json: JsValue         = Json.toJson(declarationItemVaping)
      (json \ "commodityDescription").as[String] shouldBe "Vape liquid"
      (json \ "volume").as[String]               shouldBe "50"
      (json \ "goodsValue").as[String]           shouldBe "40"
      (json \ "valueCurrency").as[String]        shouldBe "USD"
      (json \ "originCountry").as[String]        shouldBe "US"
      (json \ "exchangeRate").as[String]         shouldBe "1.2"
      (json \ "exchangeRateDate").as[String]     shouldBe "2026-09-01"
      (json \ "goodsValueGBP").as[String]        shouldBe "30.41"
      (json \ "VATRESClaimed").as[Boolean]       shouldBe false
      (json \ "exciseGBP").as[String]            shouldBe "12"
      (json \ "customsGBP").as[String]           shouldBe "1.5"
      (json \ "vatGBP").as[String]               shouldBe "9.4"
      (json \ "ukVATPaid").as[Boolean]           shouldBe false
      (json \ "ukExcisePaid").as[Boolean]        shouldBe false
      (json \ "euCustomsRelief").as[Boolean]     shouldBe false
      (json \ "madeIn").as[String]               shouldBe "US"
    }

    "deserialize from JSON" in {
      val json: JsValue         = Json.parse(
        """
          |{
          |  "commodityDescription": "Vape liquid",
          |  "volume": "50",
          |  "goodsValue": "40",
          |  "valueCurrency": "USD",
          |  "originCountry": "US",
          |  "exchangeRate": "1.2",
          |  "exchangeRateDate": "2026-09-01",
          |  "goodsValueGBP": "30.41",
          |  "VATRESClaimed": false,
          |  "exciseGBP": "12",
          |  "customsGBP": "1.5",
          |  "vatGBP": "9.4",
          |  "ukVATPaid": false,
          |  "ukExcisePaid": false,
          |  "euCustomsRelief": false,
          |  "madeIn": "US"
          |}
          |""".stripMargin
      )
      val declarationItemVaping = json.as[DeclarationItemVaping]
      declarationItemVaping.commodityDescription shouldBe Some("Vape liquid")
      declarationItemVaping.volume               shouldBe Some("50")
      declarationItemVaping.goodsValue           shouldBe Some("40")
      declarationItemVaping.valueCurrency        shouldBe Some("USD")
      declarationItemVaping.originCountry        shouldBe Some("US")
      declarationItemVaping.exchangeRate         shouldBe Some("1.2")
      declarationItemVaping.exchangeRateDate     shouldBe Some("2026-09-01")
      declarationItemVaping.goodsValueGBP        shouldBe Some("30.41")
      declarationItemVaping.VATRESClaimed        shouldBe Some(false)
      declarationItemVaping.exciseGBP            shouldBe Some("12")
      declarationItemVaping.customsGBP           shouldBe Some("1.5")
      declarationItemVaping.vatGBP               shouldBe Some("9.4")
      declarationItemVaping.ukVATPaid            shouldBe Some(false)
      declarationItemVaping.ukExcisePaid         shouldBe Some(false)
      declarationItemVaping.euCustomsRelief      shouldBe Some(false)
      declarationItemVaping.madeIn               shouldBe Some("US")
    }

    "handle missing optional fields" in {
      val json: JsValue         = Json.parse(
        """
          |{
          |  "commodityDescription": "Vape liquid"
          |}
          |""".stripMargin
      )
      val declarationItemVaping = json.as[DeclarationItemVaping]
      declarationItemVaping.commodityDescription shouldBe Some("Vape liquid")
      declarationItemVaping.volume               shouldBe None
      declarationItemVaping.goodsValue           shouldBe None
      declarationItemVaping.valueCurrency        shouldBe None
      declarationItemVaping.originCountry        shouldBe None
      declarationItemVaping.exchangeRate         shouldBe None
      declarationItemVaping.exchangeRateDate     shouldBe None
      declarationItemVaping.goodsValueGBP        shouldBe None
      declarationItemVaping.VATRESClaimed        shouldBe None
      declarationItemVaping.exciseGBP            shouldBe None
      declarationItemVaping.customsGBP           shouldBe None
      declarationItemVaping.vatGBP               shouldBe None
      declarationItemVaping.ukVATPaid            shouldBe None
      declarationItemVaping.ukExcisePaid         shouldBe None
      declarationItemVaping.euCustomsRelief      shouldBe None
      declarationItemVaping.madeIn               shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue         = Json.parse("{}")
      val declarationItemVaping = json.as[DeclarationItemVaping]
      declarationItemVaping.commodityDescription shouldBe None
      declarationItemVaping.volume               shouldBe None
      declarationItemVaping.goodsValue           shouldBe None
      declarationItemVaping.valueCurrency        shouldBe None
      declarationItemVaping.originCountry        shouldBe None
      declarationItemVaping.exchangeRate         shouldBe None
      declarationItemVaping.exchangeRateDate     shouldBe None
      declarationItemVaping.goodsValueGBP        shouldBe None
      declarationItemVaping.VATRESClaimed        shouldBe None
      declarationItemVaping.exciseGBP            shouldBe None
      declarationItemVaping.customsGBP           shouldBe None
      declarationItemVaping.vatGBP               shouldBe None
      declarationItemVaping.ukVATPaid            shouldBe None
      declarationItemVaping.ukExcisePaid         shouldBe None
      declarationItemVaping.euCustomsRelief      shouldBe None
      declarationItemVaping.madeIn               shouldBe None
    }
  }
}
