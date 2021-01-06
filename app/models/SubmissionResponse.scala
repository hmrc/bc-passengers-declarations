/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

sealed trait SubmissionResponse

object SubmissionResponse {

  case object Submitted extends SubmissionResponse
  case object Failed extends SubmissionResponse
  case object Error extends SubmissionResponse

  implicit lazy val httpReads: HttpReads[SubmissionResponse] =
    new HttpReads[SubmissionResponse] {

      override def read(method: String, url: String, response: HttpResponse): SubmissionResponse =
        response.status match {
          case NO_CONTENT =>
            Submitted
          case BAD_REQUEST =>
            Failed
          case _ =>
            Error
        }
    }
}
