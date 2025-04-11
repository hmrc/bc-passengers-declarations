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

package connectors

import javax.inject.{Inject, Singleton}
import models.SendEmailRequest
import play.api.i18n.Lang.logger.logger
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.client.HttpClientV2
import play.api.libs.ws.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class EmailErrorResponse extends NoStackTrace

@Singleton
class SendEmailConnectorImpl @Inject() (servicesConfig: ServicesConfig, val http: HttpClientV2)
    extends SendEmailConnector
    with HttpErrorFunctions {
  private val emailDomain: String = servicesConfig.getConfString("email.domain", "")
  val sendEmailURL: String        = s"${servicesConfig.baseUrl("email.sendEmailURL")}/$emailDomain/email"
}

trait SendEmailConnector extends HttpErrorFunctions {
  val http: HttpClientV2
  val sendEmailURL: String

  def requestEmail(
    EmailRequest: SendEmailRequest
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[Boolean] = {
    def errorMsg(status: String, ex: HttpException): Nothing = {
      logger.error(
        s"[EmailErrorResponse][requestEmail] PNGRS_EMAIL_FAILURE request to send email returned a $status - email not sent - reason = ${ex.getMessage}"
      )
      throw new EmailErrorResponse()
    }

    http
      .post(url"$sendEmailURL")
      .withBody(Json.toJson(EmailRequest))
      .execute[HttpResponse]
      .map { r =>
        r.status match {
          case ACCEPTED =>
            logger.debug("[SendEmailConnector][sendEmail] request to email service was successful")
            true
          case _        =>
            logger.error(
              s"[SendEmailConnector][sendEmail] PNGRS_EMAIL_FAILURE request to email service was unsuccessful with ${r.status}"
            )
            false
        }
      } recover {
      case ex: HttpException => errorMsg(ex.responseCode.toString, ex)
      case ex: Throwable     => errorMsg("0", new HttpException(ex.getMessage, 0))
    }
  }
}
