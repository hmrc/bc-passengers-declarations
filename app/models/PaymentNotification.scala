package models

import play.api.libs.json.{Json, OFormat}

object PaymentNotification {
  implicit val formats: OFormat[PaymentNotification] = Json.format

  val Successful = "Successful"
  val Failed     = "Failed"
  val Cancelled  = "Cancelled"
}
case class PaymentNotification(
  status: String,
  reference: ChargeReference
)
