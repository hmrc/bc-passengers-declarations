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

class MessageTypesSpec extends AnyWordSpec with Matchers {

  "MessageTypes" should {

    "serialize to JSON" in {
      val messageTypes  = MessageTypes(
        messageType = "Type1"
      )
      val json: JsValue = Json.toJson(messageTypes)
      (json \ "messageType").as[String] shouldBe "Type1"
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "messageType": "Type1"
          |}
          |""".stripMargin
      )
      val messageTypes  = json.as[MessageTypes]
      messageTypes.messageType shouldBe "Type1"
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      intercept[JsResultException](json.as[MessageTypes])
    }
  }
}
