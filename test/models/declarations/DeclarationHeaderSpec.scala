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

class DeclarationHeaderSpec extends AnyWordSpec with Matchers {

  "DeclarationHeader" should {

    "serialize to JSON" in {
      val declarationHeader = DeclarationHeader(
        messageTypes = MessageTypes("Type1"),
        chargeReference = "Charge1",
        portOfEntry = Some("Port1"),
        expectedDateOfArrival = Some("2023-10-01"),
        timeOfEntry = Some("10:00"),
        travellingFrom = "Country1",
        onwardTravelGBNI = "GB"
      )
      val json: JsValue     = Json.toJson(declarationHeader)
      (json \ "messageTypes" \ "messageType").as[String] shouldBe "Type1"
      (json \ "chargeReference").as[String]              shouldBe "Charge1"
      (json \ "portOfEntry").as[String]                  shouldBe "Port1"
      (json \ "expectedDateOfArrival").as[String]        shouldBe "2023-10-01"
      (json \ "timeOfEntry").as[String]                  shouldBe "10:00"
      (json \ "travellingFrom").as[String]               shouldBe "Country1"
      (json \ "onwardTravelGBNI").as[String]             shouldBe "GB"
    }

    "deserialize from JSON" in {
      val json: JsValue     = Json.parse(
        """
          |{
          |  "messageTypes": { "messageType": "Type1" },
          |  "chargeReference": "Charge1",
          |  "portOfEntry": "Port1",
          |  "expectedDateOfArrival": "2023-10-01",
          |  "timeOfEntry": "10:00",
          |  "travellingFrom": "Country1",
          |  "onwardTravelGBNI": "GB"
          |}
          |""".stripMargin
      )
      val declarationHeader = json.as[DeclarationHeader]
      declarationHeader.messageTypes.messageType shouldBe "Type1"
      declarationHeader.chargeReference          shouldBe "Charge1"
      declarationHeader.portOfEntry              shouldBe Some("Port1")
      declarationHeader.expectedDateOfArrival    shouldBe Some("2023-10-01")
      declarationHeader.timeOfEntry              shouldBe Some("10:00")
      declarationHeader.travellingFrom           shouldBe "Country1"
      declarationHeader.onwardTravelGBNI         shouldBe "GB"
    }

    "handle missing optional fields" in {
      val json: JsValue     = Json.parse(
        """
          |{
          |  "messageTypes": { "messageType": "Type1" },
          |  "chargeReference": "Charge1",
          |  "travellingFrom": "Country1",
          |  "onwardTravelGBNI": "GB"
          |}
          |""".stripMargin
      )
      val declarationHeader = json.as[DeclarationHeader]
      declarationHeader.messageTypes.messageType shouldBe "Type1"
      declarationHeader.chargeReference          shouldBe "Charge1"
      declarationHeader.portOfEntry              shouldBe None
      declarationHeader.expectedDateOfArrival    shouldBe None
      declarationHeader.timeOfEntry              shouldBe None
      declarationHeader.travellingFrom           shouldBe "Country1"
      declarationHeader.onwardTravelGBNI         shouldBe "GB"
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      intercept[JsResultException](json.as[DeclarationHeader])
    }
  }
}
