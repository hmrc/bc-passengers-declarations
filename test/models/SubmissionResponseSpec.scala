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

import org.scalatest.{FreeSpec, Inside, MustMatchers}
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

class SubmissionResponseSpec extends FreeSpec with MustMatchers {

  private val reads: HttpReads[SubmissionResponse] = implicitly[HttpReads[SubmissionResponse]]

  "a successful submission response" - {

    "must be read from an http response" in {

      val result = reads.read("POST", "/", HttpResponse(NO_CONTENT))

      result mustEqual SubmissionResponse.Submitted
    }
  }

  "a failed submission response" - {

    "must be read from an http response" in {

      val result = reads.read("POST", "/", HttpResponse(BAD_REQUEST, responseString = Some("bad request")))

      result mustEqual SubmissionResponse.Failed
    }
  }

  "an errored submission response" - {

    "must be read from an http response" in {

      val result = reads.read("POST", "/", HttpResponse(INTERNAL_SERVER_ERROR, responseString = Some("internal server error")))

      result mustEqual SubmissionResponse.Error
    }
  }
}
