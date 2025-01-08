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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*

import java.time.LocalDateTime

class LockSpec extends AnyWordSpec with Matchers {

  "Lock" should {

    "serialize to JSON" in {
      val lock          = Lock(_id = 1, lastUpdated = LocalDateTime.of(2023, 10, 1, 12, 0, 0).withNano(0))
      val json: JsValue = Json.toJson(lock)
      (json \ "_id").as[Int]            shouldBe 1
      (json \ "lastUpdated").as[String] shouldBe "2023-10-01T12:00:00"
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "_id": 1,
          |  "lastUpdated": "2023-10-01T12:00:00"
          |}
          |""".stripMargin
      )
      val lock          = json.as[Lock]
      lock._id         shouldBe 1
      lock.lastUpdated shouldBe LocalDateTime.of(2023, 10, 1, 12, 0, 0).withNano(0)
    }

    "deserialize from JSON, default json" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "_id": 1
          |}
          |""".stripMargin
      )
      val lock          = json.as[Lock]
      lock._id       shouldBe 1
      lock.lastUpdated should not be null
    }

    "handle default lastUpdated value" in {
      val lock = Lock(_id = 2)
      lock._id       shouldBe 2
      lock.lastUpdated should not be null
    }
  }
}
