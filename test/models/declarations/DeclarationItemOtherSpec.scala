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

class DeclarationItemOtherSpec extends AnyWordSpec with Matchers {

  "DeclarationItemOther" should {

    "serialize to JSON" in {
      val declarationItemOther = DeclarationItemOther(
        commodityDescription = Some("Other1"),
        quantity = Some("10"),
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
        uccRelief = Some(true),
        euCustomsRelief = Some(true),
        madeIn = Some("Country1")
      )
      val json: JsValue        = Json.toJson(declarationItemOther)
      (json \ "commodityDescription").as[String] shouldBe "Other1"
      (json \ "quantity").as[String]             shouldBe "10"
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
      (json \ "uccRelief").as[Boolean]           shouldBe true
      (json \ "euCustomsRelief").as[Boolean]     shouldBe true
      (json \ "madeIn").as[String]               shouldBe "Country1"
    }

    "deserialize from JSON" in {
      val json: JsValue        = Json.parse(
        """
          |{
          |  "commodityDescription": "Other1",
          |  "quantity": "10",
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
          |  "uccRelief": true,
          |  "euCustomsRelief": true,
          |  "madeIn": "Country1"
          |}
          |""".stripMargin
      )
      val declarationItemOther = json.as[DeclarationItemOther]
      declarationItemOther.commodityDescription shouldBe Some("Other1")
      declarationItemOther.quantity             shouldBe Some("10")
      declarationItemOther.goodsValue           shouldBe Some("100")
      declarationItemOther.valueCurrency        shouldBe Some("GBP")
      declarationItemOther.originCountry        shouldBe Some("Country1")
      declarationItemOther.exchangeRate         shouldBe Some("1.2")
      declarationItemOther.exchangeRateDate     shouldBe Some("2023-10-01")
      declarationItemOther.goodsValueGBP        shouldBe Some("120")
      declarationItemOther.VATRESClaimed        shouldBe Some(true)
      declarationItemOther.exciseGBP            shouldBe Some("10")
      declarationItemOther.customsGBP           shouldBe Some("20")
      declarationItemOther.vatGBP               shouldBe Some("30")
      declarationItemOther.ukVATPaid            shouldBe Some(true)
      declarationItemOther.uccRelief            shouldBe Some(true)
      declarationItemOther.euCustomsRelief      shouldBe Some(true)
      declarationItemOther.madeIn               shouldBe Some("Country1")
    }

    "handle missing optional fields" in {
      val json: JsValue        = Json.parse(
        """
          |{
          |  "commodityDescription": "Other1"
          |}
          |""".stripMargin
      )
      val declarationItemOther = json.as[DeclarationItemOther]
      declarationItemOther.commodityDescription shouldBe Some("Other1")
      declarationItemOther.quantity             shouldBe None
      declarationItemOther.goodsValue           shouldBe None
      declarationItemOther.valueCurrency        shouldBe None
      declarationItemOther.originCountry        shouldBe None
      declarationItemOther.exchangeRate         shouldBe None
      declarationItemOther.exchangeRateDate     shouldBe None
      declarationItemOther.goodsValueGBP        shouldBe None
      declarationItemOther.VATRESClaimed        shouldBe None
      declarationItemOther.exciseGBP            shouldBe None
      declarationItemOther.customsGBP           shouldBe None
      declarationItemOther.vatGBP               shouldBe None
      declarationItemOther.ukVATPaid            shouldBe None
      declarationItemOther.uccRelief            shouldBe None
      declarationItemOther.euCustomsRelief      shouldBe None
      declarationItemOther.madeIn               shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue        = Json.parse("{}")
      val declarationItemOther = json.as[DeclarationItemOther]
      declarationItemOther.commodityDescription shouldBe None
      declarationItemOther.quantity             shouldBe None
      declarationItemOther.goodsValue           shouldBe None
      declarationItemOther.valueCurrency        shouldBe None
      declarationItemOther.originCountry        shouldBe None
      declarationItemOther.exchangeRate         shouldBe None
      declarationItemOther.exchangeRateDate     shouldBe None
      declarationItemOther.goodsValueGBP        shouldBe None
      declarationItemOther.VATRESClaimed        shouldBe None
      declarationItemOther.exciseGBP            shouldBe None
      declarationItemOther.customsGBP           shouldBe None
      declarationItemOther.vatGBP               shouldBe None
      declarationItemOther.ukVATPaid            shouldBe None
      declarationItemOther.uccRelief            shouldBe None
      declarationItemOther.euCustomsRelief      shouldBe None
      declarationItemOther.madeIn               shouldBe None
    }
  }
}
