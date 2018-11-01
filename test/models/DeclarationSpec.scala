package models

import java.time.LocalDateTime

import models.declarations.{Declaration, State}
import org.scalatest.{FreeSpec, MustMatchers}
import play.api.libs.json.Json

class DeclarationSpec extends FreeSpec with MustMatchers {

  "a Declaration" - {

    "must deserialise" in {

      val lastUpdated = LocalDateTime.now

      val json = Json.obj(
        "_id"         -> "XHPR1234567890",
        "state"       -> State.PendingPayment,
        "data"        -> Json.obj(),
        "lastUpdated" -> Json.toJson(lastUpdated)
      )

      json.as[Declaration] mustEqual
        Declaration(
          ChargeReference(1234567890),
          State.PendingPayment,
          Json.obj(),
          lastUpdated
        )
    }

    "must serialise" in {

      val lastUpdated = LocalDateTime.now

      val json = Json.obj(
        "_id"         -> "XHPR1234567890",
        "state"       -> State.PendingPayment,
        "data"        -> Json.obj(),
        "lastUpdated" -> Json.toJson(lastUpdated)
      )

      Json.toJson(Declaration(ChargeReference(1234567890), State.PendingPayment, Json.obj(), lastUpdated)) mustEqual json
    }
  }
}
