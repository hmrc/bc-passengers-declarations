/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString, Json}

class StateSpec extends AnyWordSpec with Matchers {

  "State" when {
    "as pending-payment" must {
      "deserialise from json" in {

        JsString("pending-payment").as[State] mustEqual State.PendingPayment
      }

      "serialise to json" in {

        Json.toJson(State.PendingPayment.toString()) mustEqual JsString("pending-payment")
      }
    }

    "as paid" must {
      "deserialise from json" in {

        JsString("paid").as[State] mustEqual State.Paid
      }

      "serialise to json" in {

        Json.toJson(State.Paid.toString()) mustEqual JsString("paid")
      }
    }

    "as submission-failed" must {
      "deserialise from json" in {

        JsString("submission-failed").as[State] mustEqual State.SubmissionFailed
      }

      "serialise to json" in {

        Json.toJson(State.SubmissionFailed.toString()) mustEqual JsString("submission-failed")
      }
    }

    "as payment-failed" must {
      "deserialise from json" in {

        JsString("payment-failed").as[State] mustEqual State.PaymentFailed
      }

      "serialise to json" in {

        Json.toJson(State.PaymentFailed.toString()) mustEqual JsString("payment-failed")
      }
    }

    "as payment-cancelled" must {
      "deserialise from json" in {

        JsString("payment-cancelled").as[State] mustEqual State.PaymentCancelled
      }

      "serialise to json" in {

        Json.toJson(State.PaymentCancelled.toString()) mustEqual JsString("payment-cancelled")
      }
    }

    "as an invalid value" must {
      "fail to deserialise from json" in {

        Json.fromJson[State](JsString("foo")) mustEqual JsError("invalid declaration state")
      }
    }
  }
}
