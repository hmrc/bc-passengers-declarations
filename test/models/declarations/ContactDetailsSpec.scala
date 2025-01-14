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

class ContactDetailsSpec extends AnyWordSpec with Matchers {

  "ContactDetails" should {

    "serialize to JSON" in {
      val contactDetails = ContactDetails(
        emailAddress = Some("test@example.com")
      )
      val json: JsValue  = Json.toJson(contactDetails)
      (json \ "emailAddress").as[String] shouldBe "test@example.com"
    }

    "deserialize from JSON" in {
      val json: JsValue  = Json.parse(
        """
          |{
          |  "emailAddress": "test@example.com"
          |}
          |""".stripMargin
      )
      val contactDetails = json.as[ContactDetails]
      contactDetails.emailAddress shouldBe Some("test@example.com")
    }

    "handle missing optional fields" in {
      val json: JsValue  = Json.parse(
        """
          |{
          |}
          |""".stripMargin
      )
      val contactDetails = json.as[ContactDetails]
      contactDetails.emailAddress shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue  = Json.parse("{}")
      val contactDetails = json.as[ContactDetails]
      contactDetails.emailAddress shouldBe None
    }
  }
}
