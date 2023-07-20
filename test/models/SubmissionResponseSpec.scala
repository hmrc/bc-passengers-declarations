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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

class SubmissionResponseSpec extends AnyWordSpec with Matchers {

  private val reads: HttpReads[SubmissionResponse] = implicitly[HttpReads[SubmissionResponse]]

  "SubmissionResponse" must {
    "set to a Submitted response from a successful HttpResponse" in {

      val result = reads.read("POST", "/", HttpResponse.apply(NO_CONTENT, ""))

      result mustEqual SubmissionResponse.Submitted
    }

    "set to a Failed response from a BAD_REQUEST HttpResponse" in {

      val result = reads.read("POST", "/", HttpResponse.apply(BAD_REQUEST, "bad request"))

      result mustEqual SubmissionResponse.Failed
    }
  }

  "set to an Error response from an INTERNAL_SERVER_ERROR HttpResponse" in {

    val result = reads.read("POST", "/", HttpResponse.apply(INTERNAL_SERVER_ERROR, "internal server error"))

    result mustEqual SubmissionResponse.Error
  }
}
