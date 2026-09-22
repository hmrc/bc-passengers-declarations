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

import play.api.libs.json.{Json, OWrites}

object EtmpHip {
  implicit val writes: OWrites[EtmpHip] = Json.writes
}

object HipCustomerReference {
  implicit val writes: OWrites[HipCustomerReference] = Json.writes
}

object HipPersonalDetails {
  implicit val writes: OWrites[HipPersonalDetails] = Json.writes
}

object HipContactDetails {
  implicit val writes: OWrites[HipContactDetails] = Json.writes
}

object HipDeclarationHeader {
  implicit val writes: OWrites[HipDeclarationHeader] = Json.writes
}

object HipDeclarationTobacco {
  implicit val writes: OWrites[HipDeclarationTobacco] = Json.writes
}

object HipDeclarationItemTobacco {
  implicit val writes: OWrites[HipDeclarationItemTobacco] = Json.writes
}

object HipDeclarationAlcohol {
  implicit val writes: OWrites[HipDeclarationAlcohol] = Json.writes
}

object HipDeclarationItemAlcohol {
  implicit val writes: OWrites[HipDeclarationItemAlcohol] = Json.writes
}

object HipDeclarationVaping {
  implicit val writes: OWrites[HipDeclarationVaping] = Json.writes
}

object HipDeclarationItemVaping {
  implicit val writes: OWrites[HipDeclarationItemVaping] = Json.writes
}

object HipDeclarationOther {
  implicit val writes: OWrites[HipDeclarationOther] = Json.writes
}

object HipDeclarationItemOther {
  implicit val writes: OWrites[HipDeclarationItemOther] = Json.writes
}

object HipLiabilityDetails {
  implicit val writes: OWrites[HipLiabilityDetails] = Json.writes
}

object HipAmendmentLiabilityDetails {
  implicit val writes: OWrites[HipAmendmentLiabilityDetails] = Json.writes
}

case class EtmpHip(
  customerReference: HipCustomerReference,
  personalDetails: Option[HipPersonalDetails],
  contactDetails: Option[HipContactDetails],
  declarationHeader: HipDeclarationHeader,
  declarationTobacco: Option[HipDeclarationTobacco],
  declarationAlcohol: Option[HipDeclarationAlcohol],
  declarationVaping: Option[HipDeclarationVaping],
  declarationOther: Option[HipDeclarationOther],
  liabilityDetails: HipLiabilityDetails,
  amendmentLiabilityDetails: Option[HipAmendmentLiabilityDetails]
)

case class HipCustomerReference(
  idType: String,
  idValue: String,
  ukResident: Boolean
)

case class HipPersonalDetails(
  firstName: String,
  lastName: String
)

case class HipContactDetails(
  emailAddress: Option[String]
)

case class HipDeclarationHeader(
  chargeReference: String,
  portOfEntry: Option[String],
  expectedDateOfTravel: Option[String],
  timeOfEntry: Option[String],
  travellingFrom: String,
  onwardTravel: String
)

case class HipDeclarationTobacco(
  declItemTobacco: Option[List[HipDeclarationItemTobacco]],
  totalCustomsGbp: Option[BigDecimal],
  totalExciseGbp: Option[BigDecimal],
  totalVatGbp: Option[BigDecimal]
)

case class HipDeclarationItemTobacco(
  commodityDesc: Option[String],
  quantity: Option[String],
  weight: Option[String],
  goodsValue: Option[BigDecimal],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[BigDecimal],
  exchangeRateDate: Option[String],
  goodsValueGbp: Option[BigDecimal],
  vatResClaimed: Option[Boolean],
  exciseGbp: Option[BigDecimal],
  customsGbp: Option[BigDecimal],
  vatGbp: Option[BigDecimal],
  ukVatPaid: Option[Boolean],
  ukExcisePaid: Option[Boolean],
  euCustomsRelief: Option[Boolean],
  madeIn: Option[String]
)

case class HipDeclarationAlcohol(
  declItemAlcohol: Option[List[HipDeclarationItemAlcohol]],
  totalExciseGbp: Option[BigDecimal],
  totalCustomsGbp: Option[BigDecimal],
  totalVatGbp: Option[BigDecimal]
)

case class HipDeclarationItemAlcohol(
  commodityDesc: Option[String],
  volume: Option[String],
  goodsValue: Option[BigDecimal],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[BigDecimal],
  exchangeRateDate: Option[String],
  goodsValueGbp: Option[BigDecimal],
  vatResClaimed: Option[Boolean],
  exciseGbp: Option[BigDecimal],
  customsGbp: Option[BigDecimal],
  vatGbp: Option[BigDecimal],
  ukVatPaid: Option[Boolean],
  ukExcisePaid: Option[Boolean],
  euCustomsRelief: Option[Boolean],
  madeIn: Option[String]
)

case class HipDeclarationVaping(
  declItemVaping: Option[List[HipDeclarationItemVaping]],
  totalExciseGbp: Option[BigDecimal],
  totalCustomsGbp: Option[BigDecimal],
  totalVatGbp: Option[BigDecimal]
)

case class HipDeclarationItemVaping(
  commodityDesc: Option[String],
  volume: Option[String],
  goodsValue: Option[BigDecimal],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[BigDecimal],
  exchangeRateDate: Option[String],
  goodsValueGbp: Option[BigDecimal],
  vatResClaimed: Option[Boolean],
  exciseGbp: Option[BigDecimal],
  customsGbp: Option[BigDecimal],
  vatGbp: Option[BigDecimal],
  ukVatPaid: Option[Boolean],
  ukExcisePaid: Option[Boolean],
  euCustomsRelief: Option[Boolean],
  madeIn: Option[String]
)

case class HipDeclarationOther(
  declItemOther: Option[List[HipDeclarationItemOther]],
  totalCustomsGbp: Option[BigDecimal],
  totalExciseGbp: Option[BigDecimal],
  totalVatGbp: Option[BigDecimal]
)

case class HipDeclarationItemOther(
  commodityDesc: Option[String],
  quantity: Option[String],
  goodsValue: Option[BigDecimal],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[BigDecimal],
  exchangeRateDate: Option[String],
  goodsValueGbp: Option[BigDecimal],
  vatResClaimed: Option[Boolean],
  exciseGbp: Option[BigDecimal],
  customsGbp: Option[BigDecimal],
  vatGbp: Option[BigDecimal],
  uccRelief: Option[Boolean],
  ukVatPaid: Option[Boolean],
  euCustomsRelief: Option[Boolean],
  madeIn: Option[String]
)

case class HipLiabilityDetails(
  totalExciseGbp: Option[BigDecimal],
  totalCustomsGbp: Option[BigDecimal],
  totalVatGbp: Option[BigDecimal],
  grandTotalGbp: BigDecimal
)

case class HipAmendmentLiabilityDetails(
  additionalExciseGbp: Option[BigDecimal],
  additionalCustomsGbp: Option[BigDecimal],
  additionalVatGbp: Option[BigDecimal],
  additionalTotalGbp: Option[BigDecimal]
)
