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
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

class SubmissionResponseSpec extends AnyWordSpec with Matchers {

  private val reads: HttpReads[SubmissionResponse] = implicitly[HttpReads[SubmissionResponse]]

  "SubmissionResponse" when {
    "set to a Submitted response from a NO_CONTENT HttpResponse" in {
      // Added DDCE-7264 test: confirm success bucket.
      val result = reads.read("POST", "/", HttpResponse.apply(NO_CONTENT, ""))

      result shouldBe SubmissionResponse.Submitted
    }

    "set to a Failed response from a BAD_REQUEST HttpResponse" in {
      // Added DDCE-7264 test: confirm permanent failure bucket with detail payload.
      val body   = Json.obj("errorDetail" -> Json.obj("sourceFaultDetail" -> Json.obj("detail" -> Json.arr())))
      val result = reads.read("POST", "/", HttpResponse.apply(BAD_REQUEST, body, Map.empty))

      result shouldBe SubmissionResponse.Failed
    }

    "set to an Error response from an INTERNAL_SERVER_ERROR HttpResponse" in {
      val result = reads.read("POST", "/", HttpResponse.apply(INTERNAL_SERVER_ERROR, "internal server error"))

      result shouldBe SubmissionResponse.Error
    }
  }
}
