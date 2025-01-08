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

package models

import helpers.Constants
import models.declarations.{Declaration, State}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class DeclarationSpec extends AnyWordSpec with Matchers with Constants {

  "Declaration" when {
    "deserialise" in {

      val json = Json.obj(
        "_id"           -> "XHPR1234567890",
        "state"         -> State.PendingPayment.toString,
        "sentToEtmp"    -> false,
        "correlationId" -> correlationId,
        "journeyData"   -> Json.obj(),
        "data"          -> Json.obj(),
        "lastUpdated"   -> Json.toJson(lastUpdated)
      )

      json.as[Declaration] shouldBe
        Declaration(
          chargeReference,
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

    "serialise" in {

      val json = Json.obj(
        "_id"           -> "XHPR1234567890",
        "state"         -> State.PendingPayment.toString(),
        "sentToEtmp"    -> false,
        "correlationId" -> correlationId,
        "journeyData"   -> Json.obj(),
        "data"          -> Json.obj(),
        "lastUpdated"   -> Json.toJson(lastUpdated)
      )

      Json.toJson(
        Declaration(
          chargeReference,
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
      ) shouldBe json
    }

    "deserialise with amendState and amendSentToEtmp" in {

      val json = Json.obj(
        "_id"             -> "XHPR1234567890",
        "state"           -> State.PendingPayment.toString,
        "amendState"      -> State.PendingPayment.toString,
        "sentToEtmp"      -> false,
        "amendSentToEtmp" -> false,
        "correlationId"   -> correlationId,
        "journeyData"     -> Json.obj(),
        "data"            -> Json.obj(),
        "lastUpdated"     -> Json.toJson(lastUpdated)
      )

      json.as[Declaration] shouldBe
        Declaration(
          chargeReference,
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

    "serialise with amendState, amendCorrelationId and amendSentToEtmp" in {

      val amendCorrelationId = correlationId

      val json = Json.obj(
        "_id"                -> "XHPR1234567890",
        "state"              -> State.PendingPayment.toString,
        "amendState"         -> State.PendingPayment.toString,
        "sentToEtmp"         -> false,
        "amendSentToEtmp"    -> false,
        "correlationId"      -> correlationId,
        "amendCorrelationId" -> amendCorrelationId,
        "journeyData"        -> Json.obj(),
        "data"               -> Json.obj(),
        "lastUpdated"        -> Json.toJson(lastUpdated)
      )

      Json.toJson(
        Declaration(
          chargeReference,
          State.PendingPayment,
          Some(State.PendingPayment),
          sentToEtmp = false,
          amendSentToEtmp = Some(false),
          correlationId,
          Some(amendCorrelationId),
          Json.obj(),
          Json.obj(),
          None,
          lastUpdated
        )
      ) shouldBe json
    }
  }
}
