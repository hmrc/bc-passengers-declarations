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

package models.ChargeRefJsons

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class ChargeRefJson(
  _id: String,
  chargeReference: Int
)

object ChargeRefJson {

  implicit val formatInstant: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit lazy val reads: Reads[ChargeRefJson] = {

    import play.api.libs.functional.syntax._
    ((__ \ "_id").read[String] and
      (__ \ "chargeReference").read[Int])(ChargeRefJson.apply)
  }

  implicit lazy val writes: OWrites[ChargeRefJson] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[String] and
        (__ \ "chargeReference").write[Int]
    )(o => Tuple.fromProductTyped(o))
  }

  implicit val format: OFormat[ChargeRefJson] = OFormat(
    reads,
    writes
  )
}
