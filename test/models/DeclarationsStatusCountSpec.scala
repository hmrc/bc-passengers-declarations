/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class DeclarationsStatusCountSpec extends AnyWordSpec with Matchers {

  "DeclarationStatusCount" must {
    "deserialise" in {
      val json = Json.obj("messageState" -> "pending-payment", "count" -> 1)
      json.as[DeclarationsStatusCount] mustEqual DeclarationsStatusCount("pending-payment", 1)
    }

    ".toDeclarationsStatus" must {
      "map received DeclarationStatusCount into a DeclarationStatus" in {
        val exampleStatusCountList: List[DeclarationsStatusCount] =
          List(
            DeclarationsStatusCount("pending-payment", 1),
            DeclarationsStatusCount("paid", 0),
            DeclarationsStatusCount("payment-failed", 3),
            DeclarationsStatusCount("payment-cancelled", 1),
            DeclarationsStatusCount("submission-failed", 1)
          )

        val result = DeclarationsStatusCount.toDeclarationsStatus(exampleStatusCountList)

        result mustEqual DeclarationsStatus(1, 0, 3, 1, 1)
      }
    }
  }
}
