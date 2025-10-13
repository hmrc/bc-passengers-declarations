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

import play.api.i18n.Lang.logger.logger
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
          case NO_CONTENT  =>
            // Added DDCE-7264 handling: keep success path explicit.
            Submitted
          case BAD_REQUEST =>
            // Added DDCE-7264 handling: capture DES detail for permanent failures.
            val detail = (response.json \ "errorDetail" \ "sourceFaultDetail").asOpt[String].getOrElse("n/a")
            logger.error(
              s"[SubmissionResponse][read] PNGRS_DES_SUBMISSION_FAILURE bad request from DES (EIS); status=${response.status}; detail=$detail"
            )
            Failed
          case _           =>
            logger.error(
              s"[SubmissionResponse][read] PNGRS_DES_SUBMISSION_FAILURE  [SubmissionResponse] call to DES (EIS) is failed, Response Code is : ${response.status}"
            )
            Error
        }
    }
}
