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

class CustomerReferenceSpec extends AnyWordSpec with Matchers {

  "CustomerReference" should {

    "serialize to JSON" in {
      val customerReference = CustomerReference(
        idType = "Type1",
        idValue = "Value1",
        ukResident = true
      )
      val json: JsValue     = Json.toJson(customerReference)
      (json \ "idType").as[String]      shouldBe "Type1"
      (json \ "idValue").as[String]     shouldBe "Value1"
      (json \ "ukResident").as[Boolean] shouldBe true
    }

    "deserialize from JSON" in {
      val json: JsValue     = Json.parse(
        """
          |{
          |  "idType": "Type1",
          |  "idValue": "Value1",
          |  "ukResident": true
          |}
          |""".stripMargin
      )
      val customerReference = json.as[CustomerReference]
      customerReference.idType     shouldBe "Type1"
      customerReference.idValue    shouldBe "Value1"
      customerReference.ukResident shouldBe true
    }

    "handle missing optional fields" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "idType": "Type1",
          |  "idValue": "Value1"
          |}
          |""".stripMargin
      )
      assertThrows[JsResultException] {
        json.as[CustomerReference]
      }
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[CustomerReference]
      }
    }
  }
}
