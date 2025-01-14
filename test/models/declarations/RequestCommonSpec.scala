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

class RequestCommonSpec extends AnyWordSpec with Matchers {

  "RequestCommon" should {

    "serialize to JSON" in {
      val requestCommon = RequestCommon(
        receiptDate = "2023-10-01",
        acknowledgementReference = "ACK123",
        requestParameters = List(RequestParameters("param1", "value1"))
      )
      val json: JsValue = Json.toJson(requestCommon)
      (json \ "receiptDate").as[String]                                 shouldBe "2023-10-01"
      (json \ "acknowledgementReference").as[String]                    shouldBe "ACK123"
      (json \ "requestParameters").as[List[JsValue]].head \ "paramName" shouldBe JsDefined(JsString("param1"))
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "receiptDate": "2023-10-01",
          |  "acknowledgementReference": "ACK123",
          |  "requestParameters": [
          |    { "paramName": "param1", "paramValue": "value1" }
          |  ]
          |}
          |""".stripMargin
      )
      val requestCommon = json.as[RequestCommon]
      requestCommon.receiptDate                      shouldBe "2023-10-01"
      requestCommon.acknowledgementReference         shouldBe "ACK123"
      requestCommon.requestParameters.head.paramName shouldBe "param1"
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[RequestCommon]
      }
    }
  }
}
