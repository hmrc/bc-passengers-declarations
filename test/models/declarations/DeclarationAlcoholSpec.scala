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

class DeclarationAlcoholSpec extends AnyWordSpec with Matchers {

  "DeclarationAlcohol" should {

    "serialize to JSON" in {
      val declarationAlcohol = DeclarationAlcohol(
        totalExciseAlcohol = Some("100"),
        totalCustomsAlcohol = Some("200"),
        totalVATAlcohol = Some("300"),
        declarationItemAlcohol = Some(
          List(
            DeclarationItemAlcohol(
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
          )
        )
      )
      val json: JsValue      = Json.toJson(declarationAlcohol)
      (json \ "totalExciseAlcohol").as[String]                                          shouldBe "100"
      (json \ "totalCustomsAlcohol").as[String]                                         shouldBe "200"
      (json \ "totalVATAlcohol").as[String]                                             shouldBe "300"
      (json \ "declarationItemAlcohol").as[List[JsValue]].head \ "commodityDescription" shouldBe JsDefined(
        JsString("Alcohol1")
      )
    }

    "deserialize from JSON" in {
      val json: JsValue      = Json.parse(
        """
          |{
          |  "totalExciseAlcohol": "100",
          |  "totalCustomsAlcohol": "200",
          |  "totalVATAlcohol": "300",
          |  "declarationItemAlcohol": [
          |    {
          |      "commodityDescription": "Alcohol1",
          |      "volume": "1L",
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
      val declarationAlcohol = json.as[DeclarationAlcohol]
      declarationAlcohol.totalExciseAlcohol                                   shouldBe Some("100")
      declarationAlcohol.totalCustomsAlcohol                                  shouldBe Some("200")
      declarationAlcohol.totalVATAlcohol                                      shouldBe Some("300")
      declarationAlcohol.declarationItemAlcohol.get.head.commodityDescription shouldBe Some("Alcohol1")
    }

    "handle missing optional fields" in {
      val json: JsValue      = Json.parse(
        """
          |{
          |  "totalExciseAlcohol": "100"
          |}
          |""".stripMargin
      )
      val declarationAlcohol = json.as[DeclarationAlcohol]
      declarationAlcohol.totalExciseAlcohol     shouldBe Some("100")
      declarationAlcohol.totalCustomsAlcohol    shouldBe None
      declarationAlcohol.totalVATAlcohol        shouldBe None
      declarationAlcohol.declarationItemAlcohol shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue      = Json.parse("{}")
      val declarationAlcohol = json.as[DeclarationAlcohol]
      declarationAlcohol.totalExciseAlcohol     shouldBe None
      declarationAlcohol.totalCustomsAlcohol    shouldBe None
      declarationAlcohol.totalVATAlcohol        shouldBe None
      declarationAlcohol.declarationItemAlcohol shouldBe None
    }
  }
}
