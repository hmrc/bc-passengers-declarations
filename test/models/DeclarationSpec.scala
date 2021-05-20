/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import java.time.{LocalDateTime, ZoneOffset}
import models.declarations.{Declaration, State}
import org.scalatest.{FreeSpec, MustMatchers}
import play.api.libs.json.Json

class DeclarationSpec extends FreeSpec with MustMatchers {

  "a Declaration" - {

    "must deserialise" in {

      val lastUpdated = LocalDateTime.now(ZoneOffset.UTC)

      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

      val json = Json.obj(
        "_id"           -> "XHPR1234567890",
        "state"         -> State.PendingPayment,
        "sentToEtmp"    -> false,
        "correlationId" -> correlationId,
        "journeyData"   -> Json.obj(),
        "data"          -> Json.obj(),
        "lastUpdated"   -> Json.toJson(lastUpdated)
      )

      json.as[Declaration] mustEqual
        Declaration(
          ChargeReference(1234567890),
          State.PendingPayment,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          lastUpdated
        )
    }

    "must serialise" in {

      val lastUpdated = LocalDateTime.now(ZoneOffset.UTC)

      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"


      val json = Json.obj(
        "_id"           -> "XHPR1234567890",
        "state"         -> State.PendingPayment,
        "sentToEtmp"    -> false,
        "correlationId" -> correlationId,
        "journeyData"   -> Json.obj(),
        "data"          -> Json.obj(),
        "lastUpdated"   -> Json.toJson(lastUpdated)
      )

      Json.toJson(Declaration(ChargeReference(1234567890), State.PendingPayment, None, sentToEtmp = false, None, correlationId, None, Json.obj(), Json.obj(), None, lastUpdated)) mustEqual json
    }

    "must deserialise with amendState and amendSentToEtmp" in {

      val lastUpdated = LocalDateTime.now(ZoneOffset.UTC)

      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

      val json = Json.obj(
        "_id"         -> "XHPR1234567890",
        "state"              -> State.PendingPayment,
        "amendState"         -> State.PendingPayment,
        "sentToEtmp"         -> false,
        "amendSentToEtmp"    -> false,
        "correlationId"      -> correlationId,
        "journeyData"        -> Json.obj(),
        "data"               -> Json.obj(),
        "lastUpdated"        -> Json.toJson(lastUpdated)
      )

      json.as[Declaration] mustEqual
        Declaration(
          ChargeReference(1234567890),
          State.PendingPayment,
          Some(State.PendingPayment),
          sentToEtmp = false,
          amendSentToEtmp = Some(false),
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          lastUpdated
        )
    }

    "must serialise with amendState, amendCorrelationId and amendSentToEtmp" in {

      val lastUpdated = LocalDateTime.now(ZoneOffset.UTC)

      val amendCorrelationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"


      val json = Json.obj(
        "_id"           -> "XHPR1234567890",
        "state"                -> State.PendingPayment,
        "amendState"           -> State.PendingPayment,
        "sentToEtmp"           -> false,
        "amendSentToEtmp"      -> false,
        "correlationId"        -> correlationId,
        "amendCorrelationId"   -> amendCorrelationId,
        "journeyData"          -> Json.obj(),
        "data"                 -> Json.obj(),
        "lastUpdated"          -> Json.toJson(lastUpdated)
      )

      Json.toJson(Declaration(ChargeReference(1234567890), State.PendingPayment, Some(State.PendingPayment), sentToEtmp = false, amendSentToEtmp = Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, lastUpdated)) mustEqual json
    }
  }
}
