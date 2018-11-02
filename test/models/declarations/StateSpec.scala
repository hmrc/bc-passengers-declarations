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

  "a failed state" - {

    "must deserialise from json" in {

      JsString("failed").as[State] mustEqual State.Failed
    }

    "must serialise to json" in {

      Json.toJson(State.Failed) mustEqual JsString("failed")
    }
  }

  "a state" - {

    "must fail to deserialise from invalid json" in {

      Json.fromJson[State](JsString("foo")) mustEqual JsError("invalid declaration state")
    }
  }
}
