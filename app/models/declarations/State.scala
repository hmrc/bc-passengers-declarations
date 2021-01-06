/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models.declarations

import play.api.libs.json._

sealed trait State

object State {

  case object PendingPayment extends State
  case object Paid extends State
  case object SubmissionFailed extends State
  case object PaymentFailed extends State
  case object PaymentCancelled extends State

  implicit lazy val reads: Reads[State] = Reads {
    case JsString("pending-payment")   => JsSuccess(PendingPayment)
    case JsString("paid")              => JsSuccess(Paid)
    case JsString("submission-failed") => JsSuccess(SubmissionFailed)
    case JsString("payment-failed")    => JsSuccess(PaymentFailed)
    case JsString("payment-cancelled") => JsSuccess(PaymentCancelled)
    case _                             => JsError("invalid declaration state")
  }

  implicit lazy val writes: Writes[State] = Writes {
    case PendingPayment   => JsString("pending-payment")
    case Paid             => JsString("paid")
    case SubmissionFailed => JsString("submission-failed")
    case PaymentFailed    => JsString("payment-failed")
    case PaymentCancelled => JsString("payment-cancelled")
  }
}
