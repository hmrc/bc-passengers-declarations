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
import play.api.libs.json.*

class AmendmentLiabilityDetailsSpec extends AnyWordSpec with Matchers {

  "AmendmentLiabilityDetails" should {

    "serialize to JSON" in {
      val amendmentLiabilityDetails = AmendmentLiabilityDetails(
        additionalExciseGBP = Some("10"),
        additionalCustomsGBP = Some("20"),
        additionalVATGBP = Some("30"),
        additionalTotalGBP = Some("60")
      )
      val json: JsValue             = Json.toJson(amendmentLiabilityDetails)
      (json \ "additionalExciseGBP").as[String]  shouldBe "10"
      (json \ "additionalCustomsGBP").as[String] shouldBe "20"
      (json \ "additionalVATGBP").as[String]     shouldBe "30"
      (json \ "additionalTotalGBP").as[String]   shouldBe "60"
    }

    "deserialize from JSON" in {
      val json: JsValue             = Json.parse(
        """
          |{
          |  "additionalExciseGBP": "10",
          |  "additionalCustomsGBP": "20",
          |  "additionalVATGBP": "30",
          |  "additionalTotalGBP": "60"
          |}
          |""".stripMargin
      )
      val amendmentLiabilityDetails = json.as[AmendmentLiabilityDetails]
      amendmentLiabilityDetails.additionalExciseGBP  shouldBe Some("10")
      amendmentLiabilityDetails.additionalCustomsGBP shouldBe Some("20")
      amendmentLiabilityDetails.additionalVATGBP     shouldBe Some("30")
      amendmentLiabilityDetails.additionalTotalGBP   shouldBe Some("60")
    }

    "handle missing optional fields" in {
      val json: JsValue             = Json.parse(
        """
          |{
          |  "additionalExciseGBP": "10"
          |}
          |""".stripMargin
      )
      val amendmentLiabilityDetails = json.as[AmendmentLiabilityDetails]
      amendmentLiabilityDetails.additionalExciseGBP  shouldBe Some("10")
      amendmentLiabilityDetails.additionalCustomsGBP shouldBe None
      amendmentLiabilityDetails.additionalVATGBP     shouldBe None
      amendmentLiabilityDetails.additionalTotalGBP   shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue             = Json.parse("{}")
      val amendmentLiabilityDetails = json.as[AmendmentLiabilityDetails]
      amendmentLiabilityDetails.additionalExciseGBP  shouldBe None
      amendmentLiabilityDetails.additionalCustomsGBP shouldBe None
      amendmentLiabilityDetails.additionalVATGBP     shouldBe None
      amendmentLiabilityDetails.additionalTotalGBP   shouldBe None
    }
  }
}
