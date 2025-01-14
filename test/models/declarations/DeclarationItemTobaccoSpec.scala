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

package models.declarations

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class DeclarationItemTobaccoSpec extends AnyWordSpec with Matchers {

  "DeclarationItemTobacco" should {

    "serialize to JSON" in {
      val declarationItemTobacco = DeclarationItemTobacco(
        commodityDescription = Some("Tobacco1"),
        quantity = Some("10"),
        weight = Some("1kg"),
        goodsValue = Some("100"),
        valueCurrency = Some("GBP"),
        originCountry = Some("Country1"),
        exchangeRate = Some("1.2"),
        exchangeRateDate = Some("2023-10-01"),
        goodsValueGBP = Some("120"),
        VATRESClaimed = Some(true),
        exciseGBP = Some("10"),
        customsGBP = Some("20"),
        vatGBP = Some("30"),
        ukVATPaid = Some(true),
        ukExcisePaid = Some(true),
        euCustomsRelief = Some(true),
        madeIn = Some("Country1")
      )
      val json: JsValue          = Json.toJson(declarationItemTobacco)
      (json \ "commodityDescription").as[String] shouldBe "Tobacco1"
      (json \ "quantity").as[String]             shouldBe "10"
      (json \ "weight").as[String]               shouldBe "1kg"
      (json \ "goodsValue").as[String]           shouldBe "100"
      (json \ "valueCurrency").as[String]        shouldBe "GBP"
      (json \ "originCountry").as[String]        shouldBe "Country1"
      (json \ "exchangeRate").as[String]         shouldBe "1.2"
      (json \ "exchangeRateDate").as[String]     shouldBe "2023-10-01"
      (json \ "goodsValueGBP").as[String]        shouldBe "120"
      (json \ "VATRESClaimed").as[Boolean]       shouldBe true
      (json \ "exciseGBP").as[String]            shouldBe "10"
      (json \ "customsGBP").as[String]           shouldBe "20"
      (json \ "vatGBP").as[String]               shouldBe "30"
      (json \ "ukVATPaid").as[Boolean]           shouldBe true
      (json \ "ukExcisePaid").as[Boolean]        shouldBe true
      (json \ "euCustomsRelief").as[Boolean]     shouldBe true
      (json \ "madeIn").as[String]               shouldBe "Country1"
    }

    "deserialize from JSON" in {
      val json: JsValue          = Json.parse(
        """
          |{
          |  "commodityDescription": "Tobacco1",
          |  "quantity": "10",
          |  "weight": "1kg",
          |  "goodsValue": "100",
          |  "valueCurrency": "GBP",
          |  "originCountry": "Country1",
          |  "exchangeRate": "1.2",
          |  "exchangeRateDate": "2023-10-01",
          |  "goodsValueGBP": "120",
          |  "VATRESClaimed": true,
          |  "exciseGBP": "10",
          |  "customsGBP": "20",
          |  "vatGBP": "30",
          |  "ukVATPaid": true,
          |  "ukExcisePaid": true,
          |  "euCustomsRelief": true,
          |  "madeIn": "Country1"
          |}
          |""".stripMargin
      )
      val declarationItemTobacco = json.as[DeclarationItemTobacco]
      declarationItemTobacco.commodityDescription shouldBe Some("Tobacco1")
      declarationItemTobacco.quantity             shouldBe Some("10")
      declarationItemTobacco.weight               shouldBe Some("1kg")
      declarationItemTobacco.goodsValue           shouldBe Some("100")
      declarationItemTobacco.valueCurrency        shouldBe Some("GBP")
      declarationItemTobacco.originCountry        shouldBe Some("Country1")
      declarationItemTobacco.exchangeRate         shouldBe Some("1.2")
      declarationItemTobacco.exchangeRateDate     shouldBe Some("2023-10-01")
      declarationItemTobacco.goodsValueGBP        shouldBe Some("120")
      declarationItemTobacco.VATRESClaimed        shouldBe Some(true)
      declarationItemTobacco.exciseGBP            shouldBe Some("10")
      declarationItemTobacco.customsGBP           shouldBe Some("20")
      declarationItemTobacco.vatGBP               shouldBe Some("30")
      declarationItemTobacco.ukVATPaid            shouldBe Some(true)
      declarationItemTobacco.ukExcisePaid         shouldBe Some(true)
      declarationItemTobacco.euCustomsRelief      shouldBe Some(true)
      declarationItemTobacco.madeIn               shouldBe Some("Country1")
    }

    "handle missing optional fields" in {
      val json: JsValue          = Json.parse(
        """
          |{
          |  "commodityDescription": "Tobacco1"
          |}
          |""".stripMargin
      )
      val declarationItemTobacco = json.as[DeclarationItemTobacco]
      declarationItemTobacco.commodityDescription shouldBe Some("Tobacco1")
      declarationItemTobacco.quantity             shouldBe None
      declarationItemTobacco.weight               shouldBe None
      declarationItemTobacco.goodsValue           shouldBe None
      declarationItemTobacco.valueCurrency        shouldBe None
      declarationItemTobacco.originCountry        shouldBe None
      declarationItemTobacco.exchangeRate         shouldBe None
      declarationItemTobacco.exchangeRateDate     shouldBe None
      declarationItemTobacco.goodsValueGBP        shouldBe None
      declarationItemTobacco.VATRESClaimed        shouldBe None
      declarationItemTobacco.exciseGBP            shouldBe None
      declarationItemTobacco.customsGBP           shouldBe None
      declarationItemTobacco.vatGBP               shouldBe None
      declarationItemTobacco.ukVATPaid            shouldBe None
      declarationItemTobacco.ukExcisePaid         shouldBe None
      declarationItemTobacco.euCustomsRelief      shouldBe None
      declarationItemTobacco.madeIn               shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue          = Json.parse("{}")
      val declarationItemTobacco = json.as[DeclarationItemTobacco]
      declarationItemTobacco.commodityDescription shouldBe None
      declarationItemTobacco.quantity             shouldBe None
      declarationItemTobacco.weight               shouldBe None
      declarationItemTobacco.goodsValue           shouldBe None
      declarationItemTobacco.valueCurrency        shouldBe None
      declarationItemTobacco.originCountry        shouldBe None
      declarationItemTobacco.exchangeRate         shouldBe None
      declarationItemTobacco.exchangeRateDate     shouldBe None
      declarationItemTobacco.goodsValueGBP        shouldBe None
      declarationItemTobacco.VATRESClaimed        shouldBe None
      declarationItemTobacco.exciseGBP            shouldBe None
      declarationItemTobacco.customsGBP           shouldBe None
      declarationItemTobacco.vatGBP               shouldBe None
      declarationItemTobacco.ukVATPaid            shouldBe None
      declarationItemTobacco.ukExcisePaid         shouldBe None
      declarationItemTobacco.euCustomsRelief      shouldBe None
      declarationItemTobacco.madeIn               shouldBe None
    }
  }
}
