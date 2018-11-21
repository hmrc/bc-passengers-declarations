package models

import play.api.libs.json.{Json, OFormat}

object PaymentNotification {
  implicit val formats: OFormat[PaymentNotification] = Json.format
}
case class PaymentNotification(
  status: String,
  reference: ChargeReference
)
