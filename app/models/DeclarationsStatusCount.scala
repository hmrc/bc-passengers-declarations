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

package models

import play.api.libs.json.{Json, Reads}

case class DeclarationsStatusCount (messageState: String, count: Int)

object DeclarationsStatusCount {
  implicit val reads: Reads[DeclarationsStatusCount] = Json.reads[DeclarationsStatusCount]

  def toDeclarationsStatus(counts: List[DeclarationsStatusCount]): DeclarationsStatus = {
    val resultsMap = counts.map(res => res.messageState -> res.count).toMap
    DeclarationsStatus(
      resultsMap.getOrElse("pending-payment", 0),
      resultsMap.getOrElse("paid", 0),
      resultsMap.getOrElse("payment-failed", 0),
      resultsMap.getOrElse("payment-cancelled", 0),
      resultsMap.getOrElse("submission-failed", 0)
    )
  }
}
