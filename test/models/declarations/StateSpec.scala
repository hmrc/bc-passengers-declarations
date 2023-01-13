/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{JsError, JsString, Json}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class StateSpec extends AnyFreeSpec with Matchers {

  "a pending-payment state" - {

    "must deserialise from json" in {

      JsString("pending-payment").as[State] mustEqual State.PendingPayment
    }

    "must serialise to json" in {

      Json.toJson(State.PendingPayment.toString()) mustEqual JsString("pending-payment")
    }
  }

  "a paid state" - {

    "must deserialise from json" in {

      JsString("paid").as[State] mustEqual State.Paid
    }

    "must serialise to json" in {

      Json.toJson(State.Paid.toString()) mustEqual JsString("paid")
    }
  }

  "a submission-failed state" - {

    "must deserialise from json" in {

      JsString("submission-failed").as[State] mustEqual State.SubmissionFailed
    }

    "must serialise to json" in {

      Json.toJson(State.SubmissionFailed.toString()) mustEqual JsString("submission-failed")
    }
  }

  "a payment-failed state" - {

    "must deserialise from json" in {

      JsString("payment-failed").as[State] mustEqual State.PaymentFailed
    }

    "must serialise to json" in {

      Json.toJson(State.PaymentFailed.toString()) mustEqual JsString("payment-failed")
    }
  }

  "a payment-cancelled state" - {

    "must deserialise from json" in {

      JsString("payment-cancelled").as[State] mustEqual State.PaymentCancelled
    }

    "must serialise to json" in {

      Json.toJson(State.PaymentCancelled.toString()) mustEqual JsString("payment-cancelled")
    }
  }

  "a state" - {

    "must fail to deserialise from invalid json" in {

      Json.fromJson[State](JsString("foo")) mustEqual JsError("invalid declaration state")
    }
  }
}
