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

class ErrorDetailSpec extends AnyWordSpec with Matchers {

  "ErrorDetail" should {

    "serialize to JSON" in {
      val errorDetail   = ErrorDetail(
        correlationId = "errorCorrelationId",
        errorCode = "errorCode",
        errorMessage = "errorMessage",
        source = Some("source"),
        sourceFaultDetail = Some(Detail(List("error detail"))),
        timestamp = "2018-08-08T13:57:53Z"
      )
      val json: JsValue = Json.toJson(errorDetail)
      (json \ "correlationId").as[String]     shouldBe "errorCorrelationId"
      (json \ "errorCode").as[String]         shouldBe "errorCode"
      (json \ "errorMessage").as[String]      shouldBe "errorMessage"
      (json \ "source").as[String]            shouldBe "source"
      (json \ "timestamp").as[String]         shouldBe "2018-08-08T13:57:53Z"
      (json \ "sourceFaultDetail").as[Detail] shouldBe Detail(
        List("error detail")
      )
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "correlationId": "errorCorrelationId",
          |  "errorCode": "errorCode",
          |  "errorMessage": "errorMessage",
          |  "source": "source",
          |  "sourceFaultDetail": {
          |    "detail" : [
          |      "error detail"
          |    ]
          |  },
          |  "timestamp": "2018-08-08T13:57:53Z"
          |}
          |""".stripMargin
      )
      val errorDetail   = json.as[ErrorDetail]
      errorDetail.correlationId                      shouldBe "errorCorrelationId"
      errorDetail.errorCode                          shouldBe "errorCode"
      errorDetail.errorMessage                       shouldBe "errorMessage"
      errorDetail.source                             shouldBe Some("source")
      errorDetail.sourceFaultDetail.head.detail.head shouldBe "error detail"
      errorDetail.timestamp                          shouldBe "2018-08-08T13:57:53Z"
    }

    "handle missing optional fields" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "correlationId": "errorCorrelationId",
          |  "errorCode": "errorCode",
          |  "errorMessage": "errorMessage",
          |  "timestamp": "2018-08-08T13:57:53Z"
          |}
          |""".stripMargin
      )
      val errorDetail   = json.as[ErrorDetail]
      errorDetail.correlationId shouldBe "errorCorrelationId"
      errorDetail.errorCode     shouldBe "errorCode"
      errorDetail.errorMessage  shouldBe "errorMessage"
      errorDetail.timestamp     shouldBe "2018-08-08T13:57:53Z"
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[ErrorDetail]
      }
    }
  }
}
