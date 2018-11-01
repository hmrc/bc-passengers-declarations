package models.declarations

import play.api.libs.json._

sealed trait State

object State {

  case object PendingPayment extends State
  case object Paid extends State
  case object Failed extends State

  implicit lazy val reads: Reads[State] = Reads {
    case JsString("pending-payment") => JsSuccess(PendingPayment)
    case JsString("paid")            => JsSuccess(Paid)
    case JsString("failed")          => JsSuccess(Failed)
    case _                           => JsError("invalid declaration state")
  }

  implicit lazy val writes: Writes[State] = Writes {
    case PendingPayment => JsString("pending-payment")
    case Paid           => JsString("paid")
    case Failed         => JsString("failed")
  }
}
