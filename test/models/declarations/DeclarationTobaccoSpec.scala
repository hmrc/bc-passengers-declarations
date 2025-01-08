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

class DeclarationTobaccoSpec extends AnyWordSpec with Matchers {

  "DeclarationTobacco" should {

    "serialize to JSON" in {
      val declarationTobacco = DeclarationTobacco(
        totalExciseTobacco = Some("100"),
        totalCustomsTobacco = Some("200"),
        totalVATTobacco = Some("300"),
        declarationItemTobacco = Some(
          List(
            DeclarationItemTobacco(
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
          )
        )
      )
      val json: JsValue      = Json.toJson(declarationTobacco)
      (json \ "totalExciseTobacco").as[String]                                          shouldBe "100"
      (json \ "totalCustomsTobacco").as[String]                                         shouldBe "200"
      (json \ "totalVATTobacco").as[String]                                             shouldBe "300"
      (json \ "declarationItemTobacco").as[List[JsValue]].head \ "commodityDescription" shouldBe JsDefined(
        JsString("Tobacco1")
      )
    }

    "deserialize from JSON" in {
      val json: JsValue      = Json.parse(
        """
          |{
          |  "totalExciseTobacco": "100",
          |  "totalCustomsTobacco": "200",
          |  "totalVATTobacco": "300",
          |  "declarationItemTobacco": [
          |    {
          |      "commodityDescription": "Tobacco1",
          |      "quantity": "10",
          |      "weight": "1kg",
          |      "goodsValue": "100",
          |      "valueCurrency": "GBP",
          |      "originCountry": "Country1",
          |      "exchangeRate": "1.2",
          |      "exchangeRateDate": "2023-10-01",
          |      "goodsValueGBP": "120",
          |      "VATRESClaimed": true,
          |      "exciseGBP": "10",
          |      "customsGBP": "20",
          |      "vatGBP": "30",
          |      "ukVATPaid": true,
          |      "ukExcisePaid": true,
          |      "euCustomsRelief": true,
          |      "madeIn": "Country1"
          |    }
          |  ]
          |}
          |""".stripMargin
      )
      val declarationTobacco = json.as[DeclarationTobacco]
      declarationTobacco.totalExciseTobacco                                   shouldBe Some("100")
      declarationTobacco.totalCustomsTobacco                                  shouldBe Some("200")
      declarationTobacco.totalVATTobacco                                      shouldBe Some("300")
      declarationTobacco.declarationItemTobacco.get.head.commodityDescription shouldBe Some("Tobacco1")
    }

    "handle missing optional fields" in {
      val json: JsValue      = Json.parse(
        """
          |{
          |  "totalExciseTobacco": "100"
          |}
          |""".stripMargin
      )
      val declarationTobacco = json.as[DeclarationTobacco]
      declarationTobacco.totalExciseTobacco     shouldBe Some("100")
      declarationTobacco.totalCustomsTobacco    shouldBe None
      declarationTobacco.totalVATTobacco        shouldBe None
      declarationTobacco.declarationItemTobacco shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue      = Json.parse("{}")
      val declarationTobacco = json.as[DeclarationTobacco]
      declarationTobacco.totalExciseTobacco     shouldBe None
      declarationTobacco.totalCustomsTobacco    shouldBe None
      declarationTobacco.totalVATTobacco        shouldBe None
      declarationTobacco.declarationItemTobacco shouldBe None
    }
  }
}
