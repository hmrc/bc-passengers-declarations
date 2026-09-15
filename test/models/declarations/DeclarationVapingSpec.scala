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

class DeclarationVapingSpec extends AnyWordSpec with Matchers {

  "DeclarationVaping" should {

    "serialize to JSON" in {
      val declarationVaping = DeclarationVaping(
        totalExciseVaping = Some("12"),
        totalCustomsVaping = Some("1.5"),
        totalVATVaping = Some("9.4"),
        declarationItemVaping = Some(
          List(
            DeclarationItemVaping(
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
          )
        )
      )
      val json: JsValue     = Json.toJson(declarationVaping)
      (json \ "totalExciseVaping").as[String]                                          shouldBe "12"
      (json \ "totalCustomsVaping").as[String]                                         shouldBe "1.5"
      (json \ "totalVATVaping").as[String]                                             shouldBe "9.4"
      (json \ "declarationItemVaping").as[List[JsValue]].head \ "commodityDescription" shouldBe JsDefined(
        JsString("Vape liquid")
      )
    }

    "deserialize from JSON" in {
      val json: JsValue     = Json.parse(
        """
          |{
          |  "totalExciseVaping": "12",
          |  "totalCustomsVaping": "1.5",
          |  "totalVATVaping": "9.4",
          |  "declarationItemVaping": [
          |    {
          |      "commodityDescription": "Vape liquid",
          |      "volume": "50",
          |      "goodsValue": "40",
          |      "valueCurrency": "USD",
          |      "originCountry": "US",
          |      "exchangeRate": "1.2",
          |      "exchangeRateDate": "2026-09-01",
          |      "goodsValueGBP": "30.41",
          |      "VATRESClaimed": false,
          |      "exciseGBP": "12",
          |      "customsGBP": "1.5",
          |      "vatGBP": "9.4",
          |      "ukVATPaid": false,
          |      "ukExcisePaid": false,
          |      "euCustomsRelief": false,
          |      "madeIn": "US"
          |    }
          |  ]
          |}
          |""".stripMargin
      )
      val declarationVaping = json.as[DeclarationVaping]
      declarationVaping.totalExciseVaping                                   shouldBe Some("12")
      declarationVaping.totalCustomsVaping                                  shouldBe Some("1.5")
      declarationVaping.totalVATVaping                                      shouldBe Some("9.4")
      declarationVaping.declarationItemVaping.get.head.commodityDescription shouldBe Some("Vape liquid")
    }

    "handle missing optional fields" in {
      val json: JsValue     = Json.parse(
        """
          |{
          |  "totalExciseVaping": "12"
          |}
          |""".stripMargin
      )
      val declarationVaping = json.as[DeclarationVaping]
      declarationVaping.totalExciseVaping     shouldBe Some("12")
      declarationVaping.totalCustomsVaping    shouldBe None
      declarationVaping.totalVATVaping        shouldBe None
      declarationVaping.declarationItemVaping shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue     = Json.parse("{}")
      val declarationVaping = json.as[DeclarationVaping]
      declarationVaping.totalExciseVaping     shouldBe None
      declarationVaping.totalCustomsVaping    shouldBe None
      declarationVaping.totalVATVaping        shouldBe None
      declarationVaping.declarationItemVaping shouldBe None
    }
  }
}
