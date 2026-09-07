/*
 * Copyright 2026 HM Revenue & Customs
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

package connectors

import helpers.{BaseSpec, Constants}
import models.declarations.Etmp
import play.api.libs.json.Json

class EtmpHipTransformerSpec extends BaseSpec with Constants {

  "EtmpHipTransformer.transform" should {

    "map a create declaration onto the EPID1778 shape" in {
      val etmp   = declarationData.as[Etmp]
      val result = EtmpHipTransformer.transform(etmp)

      result.customerReference.idType shouldBe "passport"
      result.customerReference.idValue shouldBe "SX12345"
      result.customerReference.ukResident shouldBe false

      result.personalDetails.map(_.firstName) shouldBe Some("John")
      result.personalDetails.map(_.lastName) shouldBe Some("Doe")

      result.declarationHeader.chargeReference shouldBe chargeReference.toString
      result.declarationHeader.travellingFrom shouldBe "ROW"
      result.declarationHeader.onwardTravel shouldBe "GB"
      result.declarationHeader.expectedDateOfTravel shouldBe Some("2018-05-31")

      result.declarationTobacco.flatMap(_.totalExciseGbp) shouldBe Some(BigDecimal("100.54"))
      result.declarationTobacco.flatMap(_.declItemTobacco.flatMap(_.headOption.map(_.commodityDesc))) shouldBe Some(
        Some("Cigarettes")
      )
      result.declarationTobacco.flatMap(_.declItemTobacco.flatMap(_.headOption.flatMap(_.exciseGbp))) shouldBe Some(
        BigDecimal("74.00")
      )

      result.declarationAlcohol.flatMap(_.totalExciseGbp) shouldBe Some(BigDecimal("2.00"))
      result.declarationOther.flatMap(_.totalCustomsGbp) shouldBe Some(BigDecimal("341.65"))

      result.declarationVaping shouldBe None

      result.liabilityDetails.grandTotalGbp shouldBe BigDecimal("1362.46")
      result.liabilityDetails.totalExciseGbp shouldBe Some(BigDecimal("102.54"))

      result.amendmentLiabilityDetails shouldBe None
    }

    "map an amendment onto the EPID1778 shape, including amendmentLiabilityDetails" in {
      val etmp   = amendmentData.as[Etmp]
      val result = EtmpHipTransformer.transform(etmp)

      result.amendmentLiabilityDetails.flatMap(_.additionalExciseGbp) shouldBe Some(BigDecimal("102.54"))
      result.amendmentLiabilityDetails.flatMap(_.additionalTotalGbp) shouldBe Some(BigDecimal("1362.46"))
    }

    "map travellingFrom \"EU Only\" to the \"EU\" enum value" in {
      val euEtmp = (declarationData deepMerge Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "requestDetail" -> Json.obj(
            "declarationHeader" -> Json.obj("travellingFrom" -> "EU Only")
          )
        )
      )).as[Etmp]

      EtmpHipTransformer.transform(euEtmp).declarationHeader.travellingFrom shouldBe "EU"
    }

    "throw for a travellingFrom value with no EPID1778 mapping (e.g. \"Great Britain\")" in {
      val gbEtmp = (declarationData deepMerge Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "requestDetail" -> Json.obj(
            "declarationHeader" -> Json.obj("travellingFrom" -> "Great Britain")
          )
        )
      )).as[Etmp]

      an[IllegalArgumentException] should be thrownBy EtmpHipTransformer.transform(gbEtmp)
    }
  }
}
