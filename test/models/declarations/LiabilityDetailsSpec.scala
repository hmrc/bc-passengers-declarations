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

class LiabilityDetailsSpec extends AnyWordSpec with Matchers {

  "LiabilityDetails" should {

    "serialize to JSON" in {
      val liabilityDetails = LiabilityDetails(
        totalExciseGBP = Some("100"),
        totalCustomsGBP = Some("200"),
        totalVATGBP = Some("300"),
        grandTotalGBP = "600"
      )
      val json: JsValue    = Json.toJson(liabilityDetails)
      (json \ "totalExciseGBP").as[String]  shouldBe "100"
      (json \ "totalCustomsGBP").as[String] shouldBe "200"
      (json \ "totalVATGBP").as[String]     shouldBe "300"
      (json \ "grandTotalGBP").as[String]   shouldBe "600"
    }

    "deserialize from JSON" in {
      val json: JsValue    = Json.parse(
        """
          |{
          |  "totalExciseGBP": "100",
          |  "totalCustomsGBP": "200",
          |  "totalVATGBP": "300",
          |  "grandTotalGBP": "600"
          |}
          |""".stripMargin
      )
      val liabilityDetails = json.as[LiabilityDetails]
      liabilityDetails.totalExciseGBP  shouldBe Some("100")
      liabilityDetails.totalCustomsGBP shouldBe Some("200")
      liabilityDetails.totalVATGBP     shouldBe Some("300")
      liabilityDetails.grandTotalGBP   shouldBe "600"
    }

    "handle missing optional fields" in {
      val json: JsValue    = Json.parse(
        """
          |{
          |  "grandTotalGBP": "600"
          |}
          |""".stripMargin
      )
      val liabilityDetails = json.as[LiabilityDetails]
      liabilityDetails.totalExciseGBP  shouldBe None
      liabilityDetails.totalCustomsGBP shouldBe None
      liabilityDetails.totalVATGBP     shouldBe None
      liabilityDetails.grandTotalGBP   shouldBe "600"
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[LiabilityDetails]
      }
    }
  }
}
