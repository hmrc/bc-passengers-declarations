/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import play.api.libs.json._

case class DeclarationResponse(
                                euCountryCheck: String,
                                arrivingNI: Boolean,
                                isOver17: Boolean,
                                isUKResident: Option[Boolean],
                                isPrivateTravel: Boolean,
                                userInformation: JsObject,
                                calculation: JsObject,
                                liabilityDetails: JsObject,
                                oldPurchaseProductInstances: JsArray
                              )

object DeclarationResponse {

  implicit lazy val reads: Reads[DeclarationResponse] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "journeyData" \ "euCountryCheck").read[String] and
        (__ \ "journeyData" \ "arrivingNICheck").read[Boolean] and
        (__ \ "journeyData" \ "ageOver17").read[Boolean] and
        (__ \ "journeyData" \ "isUKResident").readNullable[Boolean] and
        (__ \ "journeyData" \ "privateCraft").read[Boolean] and
        (__ \ "journeyData" \ "userInformation").read[JsObject] and
        (__ \ "journeyData" \ "calculatorResponse" \ "calculation").read[JsObject] and
        (__ \ "data" \ "simpleDeclarationRequest" \ "requestDetail" \ "liabilityDetails").read[JsObject] and
        (__ \ "journeyData" \ "purchasedProductInstances").read[JsArray]
      )(DeclarationResponse.apply _)
  }

  implicit lazy val writes: OWrites[DeclarationResponse] = Json.writes[DeclarationResponse]

}
