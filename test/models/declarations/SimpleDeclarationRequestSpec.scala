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

class SimpleDeclarationRequestSpec extends AnyWordSpec with Matchers {

  "SimpleDeclarationRequest" should {

    "serialize to JSON" in {
      val simpleDeclarationRequest = SimpleDeclarationRequest(
        requestCommon = RequestCommon(
          receiptDate = "2023-10-01",
          acknowledgementReference = "ACK123",
          requestParameters = List(RequestParameters("param1", "value1"))
        ),
        requestDetail = RequestDetail(
          customerReference = CustomerReference("Type1", "Value1", true),
          personalDetails = Some(PersonalDetails("John", "Doe")),
          contactDetails = ContactDetails(Some("test@example.com")),
          declarationHeader = DeclarationHeader(
            messageTypes = MessageTypes("Type1"),
            chargeReference = "Charge1",
            portOfEntry = Some("Port1"),
            expectedDateOfArrival = Some("2023-10-01"),
            timeOfEntry = Some("10:00"),
            travellingFrom = "Country1",
            onwardTravelGBNI = "GB"
          ),
          declarationTobacco = None,
          declarationAlcohol = None,
          declarationOther = None,
          liabilityDetails = LiabilityDetails(
            totalExciseGBP = Some("100"),
            totalCustomsGBP = Some("200"),
            totalVATGBP = Some("300"),
            grandTotalGBP = "600"
          ),
          amendmentLiabilityDetails = None
        )
      )
      val json: JsValue            = Json.toJson(simpleDeclarationRequest)
      (json \ "requestCommon" \ "receiptDate").as[String]                  shouldBe "2023-10-01"
      (json \ "requestDetail" \ "customerReference" \ "idType").as[String] shouldBe "Type1"
    }

    "deserialize from JSON" in {
      val json: JsValue            = Json.parse(
        """
          |{
          |  "requestCommon": {
          |    "receiptDate": "2023-10-01",
          |    "acknowledgementReference": "ACK123",
          |    "requestParameters": [
          |      { "paramName": "param1", "paramValue": "value1" }
          |    ]
          |  },
          |  "requestDetail": {
          |    "customerReference": { "idType": "Type1", "idValue": "Value1", "ukResident": true },
          |    "personalDetails": { "firstName": "John", "lastName": "Doe" },
          |    "contactDetails": { "emailAddress": "test@example.com" },
          |    "declarationHeader": {
          |      "messageTypes": { "messageType": "Type1" },
          |      "chargeReference": "Charge1",
          |      "portOfEntry": "Port1",
          |      "expectedDateOfArrival": "2023-10-01",
          |      "timeOfEntry": "10:00",
          |      "travellingFrom": "Country1",
          |      "onwardTravelGBNI": "GB"
          |    },
          |    "liabilityDetails": {
          |      "totalExciseGBP": "100",
          |      "totalCustomsGBP": "200",
          |      "totalVATGBP": "300",
          |      "grandTotalGBP": "600"
          |    }
          |  }
          |}
          |""".stripMargin
      )
      val simpleDeclarationRequest = json.as[SimpleDeclarationRequest]
      simpleDeclarationRequest.requestCommon.receiptDate              shouldBe "2023-10-01"
      simpleDeclarationRequest.requestDetail.customerReference.idType shouldBe "Type1"
    }

    "handle missing optional fields" in {
      val json: JsValue            = Json.parse(
        """
          |{
          |  "requestCommon": {
          |    "receiptDate": "2023-10-01",
          |    "acknowledgementReference": "ACK123",
          |    "requestParameters": []
          |  },
          |  "requestDetail": {
          |    "customerReference": { "idType": "Type1", "idValue": "Value1", "ukResident": true },
          |    "contactDetails": { "emailAddress": "test@example.com" },
          |    "declarationHeader": {
          |      "messageTypes": { "messageType": "Type1" },
          |      "chargeReference": "Charge1",
          |      "travellingFrom": "Country1",
          |      "onwardTravelGBNI": "GB"
          |    },
          |    "liabilityDetails": {
          |      "grandTotalGBP": "600"
          |    }
          |  }
          |}
          |""".stripMargin
      )
      val simpleDeclarationRequest = json.as[SimpleDeclarationRequest]
      simpleDeclarationRequest.requestCommon.receiptDate     shouldBe "2023-10-01"
      simpleDeclarationRequest.requestDetail.personalDetails shouldBe None
    }

    "handle empty JSON" in {
      val json: JsValue = Json.parse("{}")
      assertThrows[JsResultException] {
        json.as[SimpleDeclarationRequest]
      }
    }
  }
}
