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

import java.time.{Instant, LocalDateTime, ZoneOffset}
import models.ChargeReference
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

final case class Declaration (
  chargeReference: ChargeReference,
  state: State,
  amendState: Option[State] = None,
  sentToEtmp: Boolean,
  amendSentToEtmp: Option[Boolean] = None,
  correlationId: String,
  amendCorrelationId: Option[String] = None,
  journeyData: JsObject,
  data: JsObject,
  amendData: Option[JsObject] = None,
  lastUpdated: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)

object Declaration {

  implicit val formatInstant: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit lazy val reads: Reads[Declaration] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[ChargeReference] and
      (__ \ "state").read[State] and
      (__ \ "amendState").readNullable[State] and
      (__ \ "sentToEtmp").read[Boolean] and
      (__ \ "amendSentToEtmp").readNullable[Boolean] and
      (__ \ "correlationId").read[String] and
      (__ \ "amendCorrelationId").readNullable[String] and
      (__ \ "journeyData").read[JsObject] and
      (__ \ "data").read[JsObject] and
      (__ \ "amendData").readNullable[JsObject] and
      (__ \ "lastUpdated").read[LocalDateTime]
    )(Declaration.apply _)
  }

  implicit lazy val writes: OWrites[Declaration] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[ChargeReference] and
      (__ \ "state").write[State] and
      (__ \ "amendState").writeNullable[State] and
      (__ \ "sentToEtmp").write[Boolean] and
      (__ \ "amendSentToEtmp").writeNullable[Boolean] and
      (__ \ "correlationId").write[String] and
      (__ \ "amendCorrelationId").writeNullable[String] and
      (__ \ "journeyData").write[JsObject] and
      (__ \ "data").write[JsObject] and
      (__ \ "amendData").writeNullable[JsObject] and
      (__ \ "lastUpdated").write[LocalDateTime]
    )(unlift(Declaration.unapply))
  }

  implicit val format: OFormat[Declaration] = OFormat(
    reads,
    writes
  )
}
