/*
 * Copyright 2021 HM Revenue & Customs
 *
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
