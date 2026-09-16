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

package models.declarations

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

class EtmpHipSpec extends AnyWordSpec with Matchers {

  private val fullItemTobacco = HipDeclarationItemTobacco(
    commodityDesc = Some("Cigarettes"),
    quantity = Some("250"),
    weight = Some("0"),
    goodsValue = Some(BigDecimal(400)),
    valueCurrency = Some("USD"),
    originCountry = Some("US"),
    exchangeRate = Some(BigDecimal("1.2")),
    exchangeRateDate = Some("2026-09-01"),
    goodsValueGbp = Some(BigDecimal("304.11")),
    vatResClaimed = Some(false),
    exciseGbp = Some(BigDecimal(74)),
    customsGbp = Some(BigDecimal("79.06")),
    vatGbp = Some(BigDecimal("91.43")),
    ukVatPaid = Some(false),
    ukExcisePaid = Some(false),
    euCustomsRelief = Some(false),
    madeIn = Some("US")
  )

  private val emptyItemTobacco = HipDeclarationItemTobacco(
    commodityDesc = None,
    quantity = None,
    weight = None,
    goodsValue = None,
    valueCurrency = None,
    originCountry = None,
    exchangeRate = None,
    exchangeRateDate = None,
    goodsValueGbp = None,
    vatResClaimed = None,
    exciseGbp = None,
    customsGbp = None,
    vatGbp = None,
    ukVatPaid = None,
    ukExcisePaid = None,
    euCustomsRelief = None,
    madeIn = None
  )

  private val fullItemAlcohol = HipDeclarationItemAlcohol(
    commodityDesc = Some("Cider"),
    volume = Some("5"),
    goodsValue = Some(BigDecimal(120)),
    valueCurrency = Some("USD"),
    originCountry = Some("US"),
    exchangeRate = Some(BigDecimal("1.2")),
    exchangeRateDate = Some("2026-09-01"),
    goodsValueGbp = Some(BigDecimal("91.23")),
    vatResClaimed = Some(false),
    exciseGbp = Some(BigDecimal(2)),
    customsGbp = Some(BigDecimal("0.3")),
    vatGbp = Some(BigDecimal("18.7")),
    ukVatPaid = Some(false),
    ukExcisePaid = Some(false),
    euCustomsRelief = Some(false),
    madeIn = Some("US")
  )

  private val emptyItemAlcohol = HipDeclarationItemAlcohol(
    commodityDesc = None,
    volume = None,
    goodsValue = None,
    valueCurrency = None,
    originCountry = None,
    exchangeRate = None,
    exchangeRateDate = None,
    goodsValueGbp = None,
    vatResClaimed = None,
    exciseGbp = None,
    customsGbp = None,
    vatGbp = None,
    ukVatPaid = None,
    ukExcisePaid = None,
    euCustomsRelief = None,
    madeIn = None
  )

  private val fullItemVaping = HipDeclarationItemVaping(
    commodityDesc = Some("Vape liquid"),
    volume = Some("50"),
    goodsValue = Some(BigDecimal(40)),
    valueCurrency = Some("USD"),
    originCountry = Some("US"),
    exchangeRate = Some(BigDecimal("1.2")),
    exchangeRateDate = Some("2026-09-01"),
    goodsValueGbp = Some(BigDecimal("30.41")),
    vatResClaimed = Some(false),
    exciseGbp = Some(BigDecimal(12)),
    customsGbp = Some(BigDecimal("1.5")),
    vatGbp = Some(BigDecimal("9.4")),
    ukVatPaid = Some(false),
    ukExcisePaid = Some(false),
    euCustomsRelief = Some(false),
    madeIn = Some("US")
  )

  private val emptyItemVaping = HipDeclarationItemVaping(
    commodityDesc = None,
    volume = None,
    goodsValue = None,
    valueCurrency = None,
    originCountry = None,
    exchangeRate = None,
    exchangeRateDate = None,
    goodsValueGbp = None,
    vatResClaimed = None,
    exciseGbp = None,
    customsGbp = None,
    vatGbp = None,
    ukVatPaid = None,
    ukExcisePaid = None,
    euCustomsRelief = None,
    madeIn = None
  )

  private val fullItemOther = HipDeclarationItemOther(
    commodityDesc = Some("Television"),
    quantity = Some("1"),
    goodsValue = Some(BigDecimal(1500)),
    valueCurrency = Some("USD"),
    originCountry = Some("US"),
    exchangeRate = Some(BigDecimal("1.2")),
    exchangeRateDate = Some("2026-09-01"),
    goodsValueGbp = Some(BigDecimal("1140.42")),
    vatResClaimed = Some(false),
    exciseGbp = Some(BigDecimal(0)),
    customsGbp = Some(BigDecimal("159.65")),
    vatGbp = Some(BigDecimal("260.01")),
    uccRelief = Some(false),
    ukVatPaid = Some(false),
    euCustomsRelief = Some(false),
    madeIn = Some("US")
  )

  private val emptyItemOther = HipDeclarationItemOther(
    commodityDesc = None,
    quantity = None,
    goodsValue = None,
    valueCurrency = None,
    originCountry = None,
    exchangeRate = None,
    exchangeRateDate = None,
    goodsValueGbp = None,
    vatResClaimed = None,
    exciseGbp = None,
    customsGbp = None,
    vatGbp = None,
    uccRelief = None,
    ukVatPaid = None,
    euCustomsRelief = None,
    madeIn = None
  )

  "HipDeclarationItemTobacco" should {
    "serialize with every field populated" in {
      val json = Json.toJson(fullItemTobacco)
      (json \ "commodityDesc").as[String] shouldBe "Cigarettes"
      (json \ "madeIn").as[String]        shouldBe "US"
    }

    "serialize with every optional field absent" in {
      val json = Json.toJson(emptyItemTobacco)
      (json \ "commodityDesc").asOpt[String] shouldBe None
      (json \ "madeIn").asOpt[String]        shouldBe None
    }
  }

  "HipDeclarationTobacco" should {
    "serialize with items and totals populated" in {
      val tobacco = HipDeclarationTobacco(
        declItemTobacco = Some(List(fullItemTobacco)),
        totalCustomsGbp = Some(BigDecimal("79.06")),
        totalExciseGbp = Some(BigDecimal(74)),
        totalVatGbp = Some(BigDecimal("91.43"))
      )
      val json    = Json.toJson(tobacco)
      (json \ "declItemTobacco").as[List[JsValue]] should have size 1
      (json \ "totalExciseGbp").as[BigDecimal]   shouldBe BigDecimal(74)
    }

    "serialize with no items and no totals" in {
      val tobacco = HipDeclarationTobacco(None, None, None, None)
      val json    = Json.toJson(tobacco)
      (json \ "declItemTobacco").asOpt[List[JsValue]] shouldBe None
      (json \ "totalExciseGbp").asOpt[BigDecimal]     shouldBe None
    }
  }

  "HipDeclarationItemAlcohol" should {
    "serialize with every field populated" in {
      val json = Json.toJson(fullItemAlcohol)
      (json \ "volume").as[String] shouldBe "5"
      (json \ "madeIn").as[String] shouldBe "US"
    }

    "serialize with every optional field absent" in {
      val json = Json.toJson(emptyItemAlcohol)
      (json \ "volume").asOpt[String] shouldBe None
      (json \ "madeIn").asOpt[String] shouldBe None
    }
  }

  "HipDeclarationAlcohol" should {
    "serialize with items and totals populated" in {
      val alcohol = HipDeclarationAlcohol(
        declItemAlcohol = Some(List(fullItemAlcohol)),
        totalExciseGbp = Some(BigDecimal(2)),
        totalCustomsGbp = Some(BigDecimal("0.3")),
        totalVatGbp = Some(BigDecimal("18.7"))
      )
      val json    = Json.toJson(alcohol)
      (json \ "declItemAlcohol").as[List[JsValue]] should have size 1
      (json \ "totalVatGbp").as[BigDecimal]      shouldBe BigDecimal("18.7")
    }

    "serialize with no items and no totals" in {
      val alcohol = HipDeclarationAlcohol(None, None, None, None)
      val json    = Json.toJson(alcohol)
      (json \ "declItemAlcohol").asOpt[List[JsValue]] shouldBe None
      (json \ "totalVatGbp").asOpt[BigDecimal]        shouldBe None
    }
  }

  "HipDeclarationItemVaping" should {
    "serialize with every field populated" in {
      val json = Json.toJson(fullItemVaping)
      (json \ "volume").as[String] shouldBe "50"
      (json \ "madeIn").as[String] shouldBe "US"
    }

    "serialize with every optional field absent" in {
      val json = Json.toJson(emptyItemVaping)
      (json \ "volume").asOpt[String] shouldBe None
      (json \ "madeIn").asOpt[String] shouldBe None
    }
  }

  "HipDeclarationVaping" should {
    "serialize with items and totals populated" in {
      val vaping = HipDeclarationVaping(
        declItemVaping = Some(List(fullItemVaping)),
        totalExciseGbp = Some(BigDecimal(12)),
        totalCustomsGbp = Some(BigDecimal("1.5")),
        totalVatGbp = Some(BigDecimal("9.4"))
      )
      val json   = Json.toJson(vaping)
      (json \ "declItemVaping").as[List[JsValue]] should have size 1
      (json \ "totalExciseGbp").as[BigDecimal]  shouldBe BigDecimal(12)
    }

    "serialize with no items and no totals" in {
      val vaping = HipDeclarationVaping(None, None, None, None)
      val json   = Json.toJson(vaping)
      (json \ "declItemVaping").asOpt[List[JsValue]] shouldBe None
      (json \ "totalExciseGbp").asOpt[BigDecimal]    shouldBe None
    }
  }

  "HipDeclarationItemOther" should {
    "serialize with every field populated" in {
      val json = Json.toJson(fullItemOther)
      (json \ "uccRelief").as[Boolean] shouldBe false
      (json \ "madeIn").as[String]     shouldBe "US"
    }

    "serialize with every optional field absent" in {
      val json = Json.toJson(emptyItemOther)
      (json \ "uccRelief").asOpt[Boolean] shouldBe None
      (json \ "madeIn").asOpt[String]     shouldBe None
    }
  }

  "HipDeclarationOther" should {
    "serialize with items and totals populated" in {
      val other = HipDeclarationOther(
        declItemOther = Some(List(fullItemOther)),
        totalCustomsGbp = Some(BigDecimal("159.65")),
        totalExciseGbp = Some(BigDecimal(0)),
        totalVatGbp = Some(BigDecimal("260.01"))
      )
      val json  = Json.toJson(other)
      (json \ "declItemOther").as[List[JsValue]]  should have size 1
      (json \ "totalCustomsGbp").as[BigDecimal] shouldBe BigDecimal("159.65")
    }

    "serialize with no items and no totals" in {
      val other = HipDeclarationOther(None, None, None, None)
      val json  = Json.toJson(other)
      (json \ "declItemOther").asOpt[List[JsValue]] shouldBe None
      (json \ "totalCustomsGbp").asOpt[BigDecimal]  shouldBe None
    }
  }

  "HipCustomerReference" should {
    "serialize all fields" in {
      val json = Json.toJson(HipCustomerReference("passport", "SX12345", ukResident = false))
      (json \ "idType").as[String]      shouldBe "passport"
      (json \ "idValue").as[String]     shouldBe "SX12345"
      (json \ "ukResident").as[Boolean] shouldBe false
    }
  }

  "HipPersonalDetails" should {
    "serialize all fields" in {
      val json = Json.toJson(HipPersonalDetails("John", "Doe"))
      (json \ "firstName").as[String] shouldBe "John"
      (json \ "lastName").as[String]  shouldBe "Doe"
    }
  }

  "HipContactDetails" should {
    "serialize with emailAddress present" in {
      val json = Json.toJson(HipContactDetails(Some("john.doe@example.com")))
      (json \ "emailAddress").as[String] shouldBe "john.doe@example.com"
    }

    "serialize with emailAddress absent" in {
      val json = Json.toJson(HipContactDetails(None))
      (json \ "emailAddress").asOpt[String] shouldBe None
    }
  }

  "HipDeclarationHeader" should {
    "serialize with every optional field populated" in {
      val header = HipDeclarationHeader(
        chargeReference = "XHPR1234567890",
        portOfEntry = Some("LHR"),
        expectedDateOfTravel = Some("2026-09-10"),
        timeOfEntry = Some("13:20:00"),
        travellingFrom = "NON_EU Only",
        onwardTravel = "GB"
      )
      val json   = Json.toJson(header)
      (json \ "chargeReference").as[String] shouldBe "XHPR1234567890"
      (json \ "portOfEntry").as[String]     shouldBe "LHR"
      (json \ "travellingFrom").as[String]  shouldBe "NON_EU Only"
      (json \ "onwardTravel").as[String]    shouldBe "GB"
    }

    "serialize with every optional field absent" in {
      val header = HipDeclarationHeader(
        chargeReference = "XHPR1234567890",
        portOfEntry = None,
        expectedDateOfTravel = None,
        timeOfEntry = None,
        travellingFrom = "Great Britain",
        onwardTravel = "NI"
      )
      val json   = Json.toJson(header)
      (json \ "portOfEntry").asOpt[String]          shouldBe None
      (json \ "expectedDateOfTravel").asOpt[String] shouldBe None
      (json \ "timeOfEntry").asOpt[String]          shouldBe None
      (json \ "travellingFrom").as[String]          shouldBe "Great Britain"
    }
  }

  "HipLiabilityDetails" should {
    "serialize with every optional field populated" in {
      val json = Json.toJson(
        HipLiabilityDetails(
          totalExciseGbp = Some(BigDecimal(74)),
          totalCustomsGbp = Some(BigDecimal("79.06")),
          totalVatGbp = Some(BigDecimal("91.43")),
          grandTotalGbp = BigDecimal("244.49")
        )
      )
      (json \ "grandTotalGbp").as[BigDecimal]  shouldBe BigDecimal("244.49")
      (json \ "totalExciseGbp").as[BigDecimal] shouldBe BigDecimal(74)
    }

    "serialize with every optional field absent" in {
      val json = Json.toJson(HipLiabilityDetails(None, None, None, BigDecimal(0)))
      (json \ "grandTotalGbp").as[BigDecimal]     shouldBe BigDecimal(0)
      (json \ "totalExciseGbp").asOpt[BigDecimal] shouldBe None
    }
  }

  "HipAmendmentLiabilityDetails" should {
    "serialize with every field populated" in {
      val json = Json.toJson(
        HipAmendmentLiabilityDetails(
          additionalExciseGbp = Some(BigDecimal(10)),
          additionalCustomsGbp = Some(BigDecimal(5)),
          additionalVatGbp = Some(BigDecimal(3)),
          additionalTotalGbp = Some(BigDecimal(18))
        )
      )
      (json \ "additionalExciseGbp").as[BigDecimal] shouldBe BigDecimal(10)
      (json \ "additionalTotalGbp").as[BigDecimal]  shouldBe BigDecimal(18)
    }

    "serialize with every field absent" in {
      val json = Json.toJson(HipAmendmentLiabilityDetails(None, None, None, None))
      (json \ "additionalExciseGbp").asOpt[BigDecimal] shouldBe None
      (json \ "additionalTotalGbp").asOpt[BigDecimal]  shouldBe None
    }
  }

  "EtmpHip" should {

    val fullHeader = HipDeclarationHeader(
      chargeReference = "XHPR1234567890",
      portOfEntry = Some("LHR"),
      expectedDateOfTravel = Some("2026-09-10"),
      timeOfEntry = Some("13:20:00"),
      travellingFrom = "NON_EU Only",
      onwardTravel = "GB"
    )

    val fullLiability = HipLiabilityDetails(
      Some(BigDecimal(74)),
      Some(BigDecimal("79.06")),
      Some(BigDecimal("91.43")),
      BigDecimal("244.49")
    )

    "serialize a full create declaration with every optional section populated" in {
      val etmpHip = EtmpHip(
        customerReference = HipCustomerReference("passport", "SX12345", ukResident = false),
        personalDetails = Some(HipPersonalDetails("John", "Doe")),
        contactDetails = Some(HipContactDetails(Some("john.doe@example.com"))),
        declarationHeader = fullHeader,
        declarationTobacco = Some(
          HipDeclarationTobacco(
            Some(List(fullItemTobacco)),
            Some(BigDecimal("79.06")),
            Some(BigDecimal(74)),
            Some(BigDecimal("91.43"))
          )
        ),
        declarationAlcohol = Some(
          HipDeclarationAlcohol(
            Some(List(fullItemAlcohol)),
            Some(BigDecimal(2)),
            Some(BigDecimal("0.3")),
            Some(BigDecimal("18.7"))
          )
        ),
        declarationVaping = Some(
          HipDeclarationVaping(
            Some(List(fullItemVaping)),
            Some(BigDecimal(12)),
            Some(BigDecimal("1.5")),
            Some(BigDecimal("9.4"))
          )
        ),
        declarationOther = Some(
          HipDeclarationOther(
            Some(List(fullItemOther)),
            Some(BigDecimal("159.65")),
            Some(BigDecimal(0)),
            Some(BigDecimal("260.01"))
          )
        ),
        liabilityDetails = fullLiability,
        amendmentLiabilityDetails = None
      )

      val json = Json.toJson(etmpHip)
      (json \ "customerReference" \ "idValue").as[String]          shouldBe "SX12345"
      (json \ "personalDetails" \ "firstName").as[String]          shouldBe "John"
      (json \ "contactDetails" \ "emailAddress").as[String]        shouldBe "john.doe@example.com"
      (json \ "declarationHeader" \ "chargeReference").as[String]  shouldBe "XHPR1234567890"
      (json \ "declarationTobacco").isDefined                      shouldBe true
      (json \ "declarationAlcohol").isDefined                      shouldBe true
      (json \ "declarationVaping").isDefined                       shouldBe true
      (json \ "declarationOther").isDefined                        shouldBe true
      (json \ "liabilityDetails" \ "grandTotalGbp").as[BigDecimal] shouldBe BigDecimal("244.49")
      (json \ "amendmentLiabilityDetails").isDefined               shouldBe false
    }

    "serialize a minimal amendment with every optional section absent" in {
      val etmpHip = EtmpHip(
        customerReference = HipCustomerReference("passport", "SX12345", ukResident = false),
        personalDetails = None,
        contactDetails = None,
        declarationHeader = fullHeader,
        declarationTobacco = None,
        declarationAlcohol = None,
        declarationVaping = None,
        declarationOther = None,
        liabilityDetails = fullLiability,
        amendmentLiabilityDetails = Some(
          HipAmendmentLiabilityDetails(
            Some(BigDecimal(10)),
            Some(BigDecimal(5)),
            Some(BigDecimal(3)),
            Some(BigDecimal(18))
          )
        )
      )

      val json = Json.toJson(etmpHip)
      (json \ "personalDetails").isDefined                                       shouldBe false
      (json \ "contactDetails").isDefined                                        shouldBe false
      (json \ "declarationTobacco").isDefined                                    shouldBe false
      (json \ "declarationAlcohol").isDefined                                    shouldBe false
      (json \ "declarationVaping").isDefined                                     shouldBe false
      (json \ "declarationOther").isDefined                                      shouldBe false
      (json \ "amendmentLiabilityDetails" \ "additionalTotalGbp").as[BigDecimal] shouldBe BigDecimal(18)
    }
  }
}
