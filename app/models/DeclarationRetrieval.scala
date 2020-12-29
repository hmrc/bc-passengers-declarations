/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package models

import play.api.libs.json.{Json, OFormat}

object DeclarationRetrieval {
  implicit val formats: OFormat[DeclarationRetrieval] = Json.format
}
case class DeclarationRetrieval(
  lastName: String,
  identificationNumber: String,
  referenceNumber: ChargeReference
)

