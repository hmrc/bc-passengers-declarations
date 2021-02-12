/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import org.scalatest.{FreeSpec, MustMatchers}
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

class SubmissionResponseSpec extends FreeSpec with MustMatchers {

  private val reads: HttpReads[SubmissionResponse] = implicitly[HttpReads[SubmissionResponse]]

  "a successful submission response" - {

    "must be read from an http response" in {

      val result = reads.read("POST", "/", HttpResponse(NO_CONTENT,""))

      result mustEqual SubmissionResponse.Submitted
    }
  }

  "a failed submission response" - {

    "must be read from an http response" in {

      val result = reads.read("POST", "/", HttpResponse.apply(BAD_REQUEST, "bad request"))

      result mustEqual SubmissionResponse.Failed
    }
  }

  "an errored submission response" - {

    "must be read from an http response" in {

      val result = reads.read("POST", "/", HttpResponse(INTERNAL_SERVER_ERROR, "internal server error"))

      result mustEqual SubmissionResponse.Error
    }
  }
}
