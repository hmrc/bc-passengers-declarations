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

package models

import java.time.{LocalDateTime, ZoneOffset}
import play.api.libs.json.{Json, OFormat, OWrites, Reads, __}

final case class Lock(_id: Int, lastUpdated: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC))

object Lock {

  implicit lazy val reads: Reads[Lock] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[Int] and
        (__ \ "LocalDateTime").read[LocalDateTime]
    )(Lock.apply _)
  }

  implicit lazy val writes: OWrites[Lock] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[Int] and
        (__ \ "LocalDateTime").write[LocalDateTime]
    )(unlift(Lock.unapply))
  }

  implicit val formats: OFormat[Lock] = Json.format
}
