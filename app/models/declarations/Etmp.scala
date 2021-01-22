/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models.declarations

import play.api.libs.json.{Json, OFormat}

object Etmp {
  implicit val formats: OFormat[Etmp] = Json.format
}

object SimpleDeclarationRequest {
  implicit val formats: OFormat[SimpleDeclarationRequest] = Json.format
}

object RequestCommon {
  implicit val formats: OFormat[RequestCommon] = Json.format
}

object RequestParameters {
  implicit val formats: OFormat[RequestParameters] = Json.format
}

object RequestDetail {
  implicit val formats: OFormat[RequestDetail] = Json.format
}

object CustomerReference {
  implicit val formats: OFormat[CustomerReference] = Json.format
}

object PersonalDetails {
  implicit val formats: OFormat[PersonalDetails] = Json.format
}

object ContactDetails {
  implicit val formats: OFormat[ContactDetails] = Json.format
}

object DeclarationHeader {
  implicit val formats: OFormat[DeclarationHeader] = Json.format
}

object MessageTypes {
  implicit val formats: OFormat[MessageTypes] = Json.format
}

object DeclarationTobacco {
  implicit val formats: OFormat[DeclarationTobacco] = Json.format
}

object DeclarationItemTobacco {
  implicit val formats: OFormat[DeclarationItemTobacco] = Json.format
}

object DeclarationAlcohol {
  implicit val formats: OFormat[DeclarationAlcohol] = Json.format
}

object DeclarationItemAlcohol {
  implicit val formats: OFormat[DeclarationItemAlcohol] = Json.format
}

object DeclarationOther {
  implicit val formats: OFormat[DeclarationOther] = Json.format
}

object DeclarationItemOther {
  implicit val formats: OFormat[DeclarationItemOther] = Json.format
}

object LiabilityDetails {
  implicit val formats: OFormat[LiabilityDetails] = Json.format
}

object AmendmentLiabilityDetails {
  implicit val formats: OFormat[AmendmentLiabilityDetails] = Json.format
}

case class Etmp(
  simpleDeclarationRequest: SimpleDeclarationRequest
)

case class SimpleDeclarationRequest(
  requestCommon: RequestCommon,
  requestDetail: RequestDetail
)

case class RequestCommon(
  receiptDate: String,
  acknowledgementReference: String,
  requestParameters: List[RequestParameters]
)

case class RequestParameters(
  paramName: String,
  paramValue: String
)

case class RequestDetail(
  customerReference: CustomerReference,
  personalDetails: Option[PersonalDetails],
  contactDetails: ContactDetails,
  declarationHeader: DeclarationHeader,
  declarationTobacco: Option[DeclarationTobacco],
  declarationAlcohol: Option[DeclarationAlcohol],
  declarationOther: Option[DeclarationOther],
  liabilityDetails: LiabilityDetails,
  amendmentLiabilityDetails: Option[AmendmentLiabilityDetails]
)

case class CustomerReference(
  idType: String,
  idValue: String,
  ukResident: Boolean
)

case class PersonalDetails(
  firstName: String,
  lastName: String
)

case class ContactDetails(
  emailAddress: Option[String]
)

case class DeclarationHeader(
  messageTypes: MessageTypes,
  chargeReference: String,
  portOfEntry: Option[String],
  expectedDateOfArrival: Option[String],
  timeOfEntry: Option[String],
  travellingFrom: String,
  uccRelief: Boolean,
  ukVATPaid: Boolean,
  ukExcisePaid: Boolean,
  onwardTravelGBNI: String
)

case class MessageTypes(
  messageType: String
)

case class DeclarationTobacco(
  totalExciseTobacco: Option[String],
  totalCustomsTobacco: Option[String],
  totalVATTobacco: Option[String],
  declarationItemTobacco: Option[List[DeclarationItemTobacco]]
)

case class DeclarationItemTobacco(
  commodityDescription: Option[String],
  quantity: Option[String],
  weight: Option[String],
  goodsValue: Option[String],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[String],
  exchangeRateDate: Option[String],
  goodsValueGBP: Option[String],
  VATRESClaimed: Option[Boolean],
  exciseGBP: Option[String],
  customsGBP: Option[String],
  vatGBP: Option[String]
)

case class DeclarationAlcohol(
  totalExciseAlcohol: Option[String],
  totalCustomsAlcohol: Option[String],
  totalVATAlcohol: Option[String],
  declarationItemAlcohol: Option[List[DeclarationItemAlcohol]]
)

case class DeclarationItemAlcohol(
  commodityDescription: Option[String],
  volume: Option[String],
  goodsValue: Option[String],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[String],
  exchangeRateDate: Option[String],
  goodsValueGBP: Option[String],
  VATRESClaimed: Option[Boolean],
  exciseGBP: Option[String],
  customsGBP: Option[String],
  vatGBP: Option[String]
)

case class DeclarationOther(
  totalExciseOther: Option[String],
  totalCustomsOther: Option[String],
  totalVATOther: Option[String],
  declarationItemOther: Option[List[DeclarationItemOther]]
)

case class DeclarationItemOther(
  commodityDescription: Option[String],
  quantity: Option[String],
  goodsValue: Option[String],
  valueCurrency: Option[String],
  originCountry: Option[String],
  exchangeRate: Option[String],
  exchangeRateDate: Option[String],
  goodsValueGBP: Option[String],
  VATRESClaimed: Option[Boolean],
  exciseGBP: Option[String],
  customsGBP: Option[String],
  vatGBP: Option[String]
)

case class LiabilityDetails(
  totalExciseGBP: Option[String],
  totalCustomsGBP: Option[String],
  totalVATGBP: Option[String],
  grandTotalGBP: String
)

case class AmendmentLiabilityDetails(
  additionalExciseGBP: Option[String],
  additionalCustomsGBP: Option[String],
  additionalVATGBP: Option[String],
  additionalTotalGBP: Option[String]
)