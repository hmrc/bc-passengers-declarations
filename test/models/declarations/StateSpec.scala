/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.{FreeSpec, MustMatchers}
import play.api.libs.json.{JsError, JsString, Json}

class StateSpec extends FreeSpec with MustMatchers {

  "a pending-payment state" - {

    "must deserialise from json" in {

      JsString("pending-payment").as[State] mustEqual State.PendingPayment
    }

    "must serialise to json" in {

      Json.toJson(State.PendingPayment) mustEqual JsString("pending-payment")
    }
  }

  "a paid state" - {

    "must deserialise from json" in {

      JsString("paid").as[State] mustEqual State.Paid
    }

    "must serialise to json" in {

      Json.toJson(State.Paid) mustEqual JsString("paid")
    }
  }

  "a submission-failed state" - {

    "must deserialise from json" in {

      JsString("submission-failed").as[State] mustEqual State.SubmissionFailed
    }

    "must serialise to json" in {

      Json.toJson(State.SubmissionFailed) mustEqual JsString("submission-failed")
    }
  }

  "a payment-failed state" - {

    "must deserialise from json" in {

      JsString("payment-failed").as[State] mustEqual State.PaymentFailed
    }

    "must serialise to json" in {

      Json.toJson(State.PaymentFailed) mustEqual JsString("payment-failed")
    }
  }

  "a payment-cancelled state" - {

    "must deserialise from json" in {

      JsString("payment-cancelled").as[State] mustEqual State.PaymentCancelled
    }

    "must serialise to json" in {

      Json.toJson(State.PaymentCancelled) mustEqual JsString("payment-cancelled")
    }
  }

  "a state" - {

    "must fail to deserialise from invalid json" in {

      Json.fromJson[State](JsString("foo")) mustEqual JsError("invalid declaration state")
    }
  }
}
