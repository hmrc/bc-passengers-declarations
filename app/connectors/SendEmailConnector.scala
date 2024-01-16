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

package connectors

import javax.inject.{Inject, Singleton}
import models.SendEmailRequest
import play.api.i18n.Lang.logger.logger
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class EmailErrorResponse() extends NoStackTrace

@Singleton
class SendEmailConnectorImpl @Inject() (servicesConfig: ServicesConfig, val http: HttpClient)
    extends SendEmailConnector
    with HttpErrorFunctions {
  val emailDomain: String  = servicesConfig.getConfString("email.domain", "")
  val sendEmailURL: String = s"${servicesConfig.baseUrl("email.sendEmailURL")}/$emailDomain/email"
}

trait SendEmailConnector extends HttpErrorFunctions {
  val http: HttpClient
  val sendEmailURL: String

  def requestEmail(
    EmailRequest: SendEmailRequest
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Boolean] = {
    def errorMsg(status: String, ex: HttpException) = {
      logger.error(
        s"PNGRS_EMAIL_FAILURE [SendEmailConnector] [sendEmail] request to send email returned a $status - email not sent - reason = ${ex.getMessage}"
      )
      throw new EmailErrorResponse()
    }
    http.POST[SendEmailRequest, HttpResponse](s"$sendEmailURL", EmailRequest) map { r =>
      r.status match {
        case ACCEPTED =>
          logger.debug("[SendEmailConnector] [sendEmail] request to email service was successful")
          true
        case _        =>
          logger.error(
            s"PNGRS_EMAIL_FAILURE [SendEmailConnector] [sendEmail] request to email service was unsuccessful with ${r.status}"
          )
          false
      }
    } recover {
      case ex: HttpException => errorMsg(ex.responseCode.toString, ex)
      case ex: Throwable     => errorMsg("0", new HttpException(ex.getMessage, 0))
      case _                 => errorMsg("0", new HttpException("An exception was thrown when calling the email service", 0))
    }
  }
}
