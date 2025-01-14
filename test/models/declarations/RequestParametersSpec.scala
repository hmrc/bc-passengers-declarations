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

class RequestParametersSpec extends AnyWordSpec with Matchers {

  "RequestParameters" should {

    "serialize to JSON" in {
      val requestParameters = RequestParameters(
        paramName = "param1",
        paramValue = "value1"
      )
      val json: JsValue     = Json.toJson(requestParameters)
      (json \ "paramName").as[String]  shouldBe "param1"
      (json \ "paramValue").as[String] shouldBe "value1"
    }

    "deserialize from JSON" in {
      val json: JsValue     = Json.parse(
        """
          |{
          |  "paramName": "param1",
          |  "paramValue": "value1"
          |}
          |""".stripMargin
      )
      val requestParameters = json.as[RequestParameters]
      requestParameters.paramName  shouldBe "param1"
      requestParameters.paramValue shouldBe "value1"
    }

    "handle missing fields" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "paramName": "param1"
          |}
          |""".stripMargin
      )
      assertThrows[JsResultException] {
        json.as[RequestParameters]
      }
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[RequestParameters]
      }
    }
  }
}
