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

class DeclarationItemAlcoholSpec extends AnyWordSpec with Matchers {

  "DeclarationItemAlcohol" should {

    "serialize to JSON" in {
      val declarationItemAlcohol = DeclarationItemAlcohol(
        commodityDescription = Some("Alcohol1"),
        volume = Some("1L"),
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
      val json: JsValue          = Json.toJson(declarationItemAlcohol)
      (json \ "commodityDescription").as[String] shouldBe "Alcohol1"
      (json \ "volume").as[String]               shouldBe "1L"
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
          |  "commodityDescription": "Alcohol1",
          |  "volume": "1L",
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
      val declarationItemAlcohol = json.as[DeclarationItemAlcohol]
      declarationItemAlcohol.commodityDescription shouldBe Some("Alcohol1")
      declarationItemAlcohol.volume               shouldBe Some("1L")
      declarationItemAlcohol.goodsValue           shouldBe Some("100")
      declarationItemAlcohol.valueCurrency        shouldBe Some("GBP")
      declarationItemAlcohol.originCountry        shouldBe Some("Country1")
      declarationItemAlcohol.exchangeRate         shouldBe Some("1.2")
      declarationItemAlcohol.exchangeRateDate     shouldBe Some("2023-10-01")
      declarationItemAlcohol.goodsValueGBP        shouldBe Some("120")
      declarationItemAlcohol.VATRESClaimed        shouldBe Some(true)
      declarationItemAlcohol.exciseGBP            shouldBe Some("10")
      declarationItemAlcohol.customsGBP           shouldBe Some("20")
      declarationItemAlcohol.vatGBP               shouldBe Some("30")
      declarationItemAlcohol.ukVATPaid            shouldBe Some(true)
      declarationItemAlcohol.ukExcisePaid         shouldBe Some(true)
      declarationItemAlcohol.euCustomsRelief      shouldBe Some(true)
      declarationItemAlcohol.madeIn               shouldBe Some("Country1")
    }

    "handle missing optional fields" in {
      val json: JsValue          = Json.parse(
        """
          |{
          |  "commodityDescription": "Alcohol1"
          |}
          |""".stripMargin
      )
      val declarationItemAlcohol = json.as[DeclarationItemAlcohol]
      declarationItemAlcohol.commodityDescription shouldBe Some("Alcohol1")
      declarationItemAlcohol.volume               shouldBe None
      declarationItemAlcohol.goodsValue           shouldBe None
      declarationItemAlcohol.valueCurrency        shouldBe None
      declarationItemAlcohol.originCountry        shouldBe None
      declarationItemAlcohol.exchangeRate         shouldBe None
      declarationItemAlcohol.exchangeRateDate     shouldBe None
      declarationItemAlcohol.goodsValueGBP        shouldBe None
      declarationItemAlcohol.VATRESClaimed        shouldBe None
      declarationItemAlcohol.exciseGBP            shouldBe None
      declarationItemAlcohol.customsGBP           shouldBe None
      declarationItemAlcohol.vatGBP               shouldBe None
      declarationItemAlcohol.ukVATPaid            shouldBe None
      declarationItemAlcohol.ukExcisePaid         shouldBe None
      declarationItemAlcohol.euCustomsRelief      shouldBe None
      declarationItemAlcohol.madeIn               shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue          = Json.parse("{}")
      val declarationItemAlcohol = json.as[DeclarationItemAlcohol]
      declarationItemAlcohol.commodityDescription shouldBe None
      declarationItemAlcohol.volume               shouldBe None
      declarationItemAlcohol.goodsValue           shouldBe None
      declarationItemAlcohol.valueCurrency        shouldBe None
      declarationItemAlcohol.originCountry        shouldBe None
      declarationItemAlcohol.exchangeRate         shouldBe None
      declarationItemAlcohol.exchangeRateDate     shouldBe None
      declarationItemAlcohol.goodsValueGBP        shouldBe None
      declarationItemAlcohol.VATRESClaimed        shouldBe None
      declarationItemAlcohol.exciseGBP            shouldBe None
      declarationItemAlcohol.customsGBP           shouldBe None
      declarationItemAlcohol.vatGBP               shouldBe None
      declarationItemAlcohol.ukVATPaid            shouldBe None
      declarationItemAlcohol.ukExcisePaid         shouldBe None
      declarationItemAlcohol.euCustomsRelief      shouldBe None
      declarationItemAlcohol.madeIn               shouldBe None
    }
  }
}
