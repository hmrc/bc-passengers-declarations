/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package models

import play.api.libs.json.{Json, OFormat}

case class SendEmailRequest(

                             to: Seq[String],
                             templateId: String,
                             parameters: Map[String,String],
                             force: Boolean
                           )
object SendEmailRequest {
  implicit val format: OFormat[SendEmailRequest] = Json.format[SendEmailRequest]
}
