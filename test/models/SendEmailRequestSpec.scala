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

package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

class SendEmailRequestSpec extends AnyWordSpec with Matchers {

  "SendEmailRequest" should {

    "serialize to JSON" in {
      val request = SendEmailRequest(
        to = Seq("test@example.com"),
        templateId = "template123",
        parameters = Map("param1" -> "value1"),
        force = true
      )

      val jsonRaw: JsValue = Json.parse(
        """
          |{
          |  "to": ["test@example.com"],
          |  "templateId": "template123",
          |  "parameters": {"param1": "value1"},
          |  "force": true
          |}
          |""".stripMargin
      )

      val json: JsValue = Json.toJson(request)

      json shouldBe jsonRaw
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "to": ["test@example.com"],
          |  "templateId": "template123",
          |  "parameters": {"param1": "value1"},
          |  "force": true
          |}
          |""".stripMargin
      )

      val request = json.as[SendEmailRequest]
      request.to         shouldBe Seq("test@example.com")
      request.templateId shouldBe "template123"
      request.parameters shouldBe Map("param1" -> "value1")
      request.force      shouldBe true
    }

    "deserialize from JSON2" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "to": [],
          |  "templateId": "template123",
          |  "parameters": {"param1": "value1"},
          |  "force": true
          |}
          |""".stripMargin
      )

      val request = json.as[SendEmailRequest]
      request.to         shouldBe Seq()
      request.templateId shouldBe "template123"
      request.parameters shouldBe Map("param1" -> "value1")
      request.force      shouldBe true
    }

    "handle empty parameters map" in {
      val request = SendEmailRequest(
        to = Seq("test@example.com"),
        templateId = "template123",
        parameters = Map.empty,
        force = false
      )

      val json: JsValue = Json.toJson(request)
      (json \ "parameters").as[Map[String, String]] shouldBe empty

      val deserializedRequest = json.as[SendEmailRequest]
      deserializedRequest.parameters shouldBe empty
    }

    "handle empty 'to'" in {
      val request = SendEmailRequest(
        to = Seq(),
        templateId = "template123",
        parameters = Map.empty,
        force = false
      )

      val json: JsValue = Json.toJson(request)
      (json \ "to").as[Seq[String]] shouldBe empty

      val deserializedRequest = json.as[SendEmailRequest]
      deserializedRequest.to shouldBe empty
    }
  }
}
