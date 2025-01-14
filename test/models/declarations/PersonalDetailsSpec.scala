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

class PersonalDetailsSpec extends AnyWordSpec with Matchers {

  "PersonalDetails" should {

    "serialize to JSON" in {
      val personalDetails = PersonalDetails(
        firstName = "John",
        lastName = "Doe"
      )
      val json: JsValue   = Json.toJson(personalDetails)
      (json \ "firstName").as[String] shouldBe "John"
      (json \ "lastName").as[String]  shouldBe "Doe"
    }

    "deserialize from JSON" in {
      val json: JsValue   = Json.parse(
        """
          |{
          |  "firstName": "John",
          |  "lastName": "Doe"
          |}
          |""".stripMargin
      )
      val personalDetails = json.as[PersonalDetails]
      personalDetails.firstName shouldBe "John"
      personalDetails.lastName  shouldBe "Doe"
    }

    "handle missing optional fields" in {
      val json: JsValue = Json.parse(
        """
          |{
          |}
          |""".stripMargin
      )
      assertThrows[JsResultException] {
        json.as[PersonalDetails]
      }
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[PersonalDetails]
      }
    }
  }
}
