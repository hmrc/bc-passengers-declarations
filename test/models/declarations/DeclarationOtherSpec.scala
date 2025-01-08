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

class DeclarationOtherSpec extends AnyWordSpec with Matchers {

  "DeclarationOther" should {

    "serialize to JSON" in {
      val declarationOther = DeclarationOther(
        totalExciseOther = Some("100"),
        totalCustomsOther = Some("200"),
        totalVATOther = Some("300"),
        declarationItemOther = Some(
          List(
            DeclarationItemOther(
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
          )
        )
      )
      val json: JsValue    = Json.toJson(declarationOther)
      (json \ "totalExciseOther").as[String]                                          shouldBe "100"
      (json \ "totalCustomsOther").as[String]                                         shouldBe "200"
      (json \ "totalVATOther").as[String]                                             shouldBe "300"
      (json \ "declarationItemOther").as[List[JsValue]].head \ "commodityDescription" shouldBe JsDefined(
        JsString("Other1")
      )
    }

    "deserialize from JSON" in {
      val json: JsValue    = Json.parse(
        """
          |{
          |  "totalExciseOther": "100",
          |  "totalCustomsOther": "200",
          |  "totalVATOther": "300",
          |  "declarationItemOther": [
          |    {
          |      "commodityDescription": "Other1",
          |      "quantity": "10",
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
          |      "uccRelief": true,
          |      "euCustomsRelief": true,
          |      "madeIn": "Country1"
          |    }
          |  ]
          |}
          |""".stripMargin
      )
      val declarationOther = json.as[DeclarationOther]
      declarationOther.totalExciseOther                                   shouldBe Some("100")
      declarationOther.totalCustomsOther                                  shouldBe Some("200")
      declarationOther.totalVATOther                                      shouldBe Some("300")
      declarationOther.declarationItemOther.get.head.commodityDescription shouldBe Some("Other1")
    }

    "handle missing optional fields" in {
      val json: JsValue    = Json.parse(
        """
          |{
          |  "totalExciseOther": "100"
          |}
          |""".stripMargin
      )
      val declarationOther = json.as[DeclarationOther]
      declarationOther.totalExciseOther     shouldBe Some("100")
      declarationOther.totalCustomsOther    shouldBe None
      declarationOther.totalVATOther        shouldBe None
      declarationOther.declarationItemOther shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue    = Json.parse("{}")
      val declarationOther = json.as[DeclarationOther]
      declarationOther.totalExciseOther     shouldBe None
      declarationOther.totalCustomsOther    shouldBe None
      declarationOther.totalVATOther        shouldBe None
      declarationOther.declarationItemOther shouldBe None
    }
  }
}
