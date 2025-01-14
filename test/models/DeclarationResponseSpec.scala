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
import play.api.libs.json.*

class DeclarationResponseSpec extends AnyWordSpec with Matchers with DefaultWrites {

  "DeclarationResponse" should {

    "serialize to JSON" in {
      val response = DeclarationResponse(
        euCountryCheck = "yes",
        arrivingNI = true,
        isOver17 = true,
        isUKResident = Some(true),
        isPrivateTravel = false,
        userInformation = Json.obj("name" -> "John Doe"),
        calculation = Json.obj("total" -> 100),
        liabilityDetails = Json.obj("duty" -> 20),
        oldPurchaseProductInstances = Json.arr(Json.obj("product" -> "item1")),
        amendmentCount = Some(1),
        deltaCalculation = Some(Json.obj("delta" -> 5)),
        amendState = Some("amended")
      )

      val json: JsValue = Json.toJson(response)
      (json \ "euCountryCheck").as[String]               shouldBe "yes"
      (json \ "arrivingNI").as[Boolean]                  shouldBe true
      (json \ "isOver17").as[Boolean]                    shouldBe true
      (json \ "isUKResident").asOpt[Boolean]             shouldBe Some(true)
      (json \ "isPrivateTravel").as[Boolean]             shouldBe false
      (json \ "userInformation").as[JsObject]            shouldBe Json.obj("name" -> "John Doe")
      (json \ "calculation").as[JsObject]                shouldBe Json.obj("total" -> 100)
      (json \ "liabilityDetails").as[JsObject]           shouldBe Json.obj("duty" -> 20)
      (json \ "oldPurchaseProductInstances").as[JsArray] shouldBe Json.arr(Json.obj("product" -> "item1"))
      (json \ "amendmentCount").asOpt[Int]               shouldBe Some(1)
      (json \ "deltaCalculation").asOpt[JsObject]        shouldBe Some(Json.obj("delta" -> 5))
      (json \ "amendState").asOpt[String]                shouldBe Some("amended")
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "journeyData": {
          |    "euCountryCheck": "yes",
          |    "arrivingNICheck": true,
          |    "ageOver17": true,
          |    "isUKResident": true,
          |    "privateCraft": false,
          |    "userInformation": {"name": "John Doe"},
          |    "calculatorResponse": {"calculation": {"total": 100}},
          |    "purchasedProductInstances": [{"product": "item1"}],
          |    "amendmentCount": 1,
          |    "deltaCalculation": {"delta": 5}
          |  },
          |  "data": {
          |    "simpleDeclarationRequest": {
          |      "requestDetail": {
          |        "liabilityDetails": {"duty": 20}
          |      }
          |    }
          |  },
          |  "amendState": "amended"
          |}
          |""".stripMargin
      )

      val response = json.as[DeclarationResponse]
      response.euCountryCheck              shouldBe "yes"
      response.arrivingNI                  shouldBe true
      response.isOver17                    shouldBe true
      response.isUKResident                shouldBe Some(true)
      response.isPrivateTravel             shouldBe false
      response.userInformation             shouldBe Json.obj("name" -> "John Doe")
      response.calculation                 shouldBe Json.obj("total" -> 100)
      response.liabilityDetails            shouldBe Json.obj("duty" -> 20)
      response.oldPurchaseProductInstances shouldBe Json.arr(Json.obj("product" -> "item1"))
      response.amendmentCount              shouldBe Some(1)
      response.deltaCalculation            shouldBe Some(Json.obj("delta" -> 5))
      response.amendState                  shouldBe Some("amended")
    }

    "handle optional fields being None" in {
      val response = DeclarationResponse(
        euCountryCheck = "no",
        arrivingNI = false,
        isOver17 = false,
        isUKResident = None,
        isPrivateTravel = true,
        userInformation = Json.obj(),
        calculation = Json.obj(),
        liabilityDetails = Json.obj(),
        oldPurchaseProductInstances = Json.arr(),
        amendmentCount = None,
        deltaCalculation = None,
        amendState = None
      )

      val json: JsValue = Json.toJson(response)
      (json \ "isUKResident").asOpt[Boolean]      shouldBe None
      (json \ "amendmentCount").asOpt[Int]        shouldBe None
      (json \ "deltaCalculation").asOpt[JsObject] shouldBe None
      (json \ "amendState").asOpt[String]         shouldBe None
    }
  }
}
