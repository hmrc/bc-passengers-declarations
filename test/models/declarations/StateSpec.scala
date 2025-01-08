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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString, Json}

class StateSpec extends AnyWordSpec with Matchers {

  "State" when {
    "as pending-payment" must {
      "deserialise from json" in {

        JsString("pending-payment").as[State] shouldBe State.PendingPayment
      }

      "serialise to json" in {

        Json.toJson(State.PendingPayment.toString()) shouldBe JsString("pending-payment")
      }
    }

    "as paid" must {
      "deserialise from json" in {

        JsString("paid").as[State] shouldBe State.Paid
      }

      "serialise to json" in {

        Json.toJson(State.Paid.toString()) shouldBe JsString("paid")
      }
    }

    "as submission-failed" must {
      "deserialise from json" in {

        JsString("submission-failed").as[State] shouldBe State.SubmissionFailed
      }

      "serialise to json" in {

        Json.toJson(State.SubmissionFailed.toString()) shouldBe JsString("submission-failed")
      }
    }

    "as payment-failed" must {
      "deserialise from json" in {

        JsString("payment-failed").as[State] shouldBe State.PaymentFailed
      }

      "serialise to json" in {

        Json.toJson(State.PaymentFailed.toString()) shouldBe JsString("payment-failed")
      }
    }

    "as payment-cancelled" must {
      "deserialise from json" in {

        JsString("payment-cancelled").as[State] shouldBe State.PaymentCancelled
      }

      "serialise to json" in {

        Json.toJson(State.PaymentCancelled.toString()) shouldBe JsString("payment-cancelled")
      }
    }

    "as an invalid value" must {
      "fail to deserialise from json" in {

        Json.fromJson[State](JsString("foo")) shouldBe JsError("invalid declaration state")
      }
    }
  }
}
