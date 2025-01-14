/*
 * Copyright 2025 HM Revenue & Customs
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

package models.declarations

import play.api.libs.json._

sealed trait State

object State {

  case object PendingPayment extends State {
    override def toString(): String =
      "pending-payment"
  }
  case object Paid extends State {
    override def toString(): String =
      "paid"
  }
  case object SubmissionFailed extends State {
    override def toString(): String =
      "submission-failed"
  }
  case object PaymentFailed extends State {
    override def toString(): String =
      "payment-failed"
  }
  case object PaymentCancelled extends State {
    override def toString(): String =
      "payment-cancelled"
  }

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
