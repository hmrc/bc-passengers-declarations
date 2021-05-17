/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

sealed trait SubmissionResponse

object SubmissionResponse {

  case object Submitted extends SubmissionResponse
  case object Failed extends SubmissionResponse
  case object Error extends SubmissionResponse
  case object ParsingException extends SubmissionResponse

  implicit lazy val httpReads: HttpReads[SubmissionResponse] =
    new HttpReads[SubmissionResponse] {

      override def read(method: String, url: String, response: HttpResponse): SubmissionResponse =
        response.status match {
          case NO_CONTENT =>
            Submitted
          case BAD_REQUEST =>
            Logger.error(s"PNGRS_DES_SUBMISSION_FAILURE  [SubmissionResponse] BAD Request is received from DES (EIS), Response Code from EIS is : ${response.status}")
            Failed
          case _ =>
            Logger.error(s"PNGRS_DES_SUBMISSION_FAILURE  [SubmissionResponse] call to DES (EIS) is failed, Response Code is : ${response.status}")
            Error
        }
    }
}
