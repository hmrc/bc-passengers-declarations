/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import play.api.libs.json.{Json, OFormat}

object PreviousDeclarationRequest {
  implicit val formats: OFormat[PreviousDeclarationRequest] = Json.format
}

case class PreviousDeclarationRequest(
                                lastName: String,
                                identificationNumber: String,
                                referenceNumber: String
                              )
