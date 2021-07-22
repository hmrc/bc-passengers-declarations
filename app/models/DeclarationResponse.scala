/*
 * Copyright 2021 HM Revenue & Customs
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
                                oldPurchaseProductInstances: JsArray,
                                amendmentCount: Option[Int],
                                deltaCalculation: Option[JsObject],
                                amendState: Option[String]
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
        (__ \ "journeyData" \ "purchasedProductInstances").read[JsArray] and
        (__ \ "journeyData" \ "amendmentCount").readNullable[Int] and
        (__ \ "journeyData" \ "deltaCalculation").readNullable[JsObject] and
        (__ \ "amendState").readNullable[String]
      )(DeclarationResponse.apply _)
  }

  implicit lazy val writes: OWrites[DeclarationResponse] = Json.writes[DeclarationResponse]

}
