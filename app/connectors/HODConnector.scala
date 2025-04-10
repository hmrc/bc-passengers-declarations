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

import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import models.declarations.{Declaration, Etmp}
import models.{Service, SubmissionResponse}
import org.apache.pekko.pattern.CircuitBreaker
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.{JsError, JsObject, Json}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import play.api.libs.ws.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
  httpClientV2: HttpClientV2,
  config: Configuration,
  @Named("des") circuitBreaker: CircuitBreaker
)(implicit ec: ExecutionContext)
    extends HttpDate {

  private val baseUrl            = config.get[Service]("microservice.services.des")
  private val declarationFullUrl = s"$baseUrl/declarations/passengerdeclaration/v1"

  private val bearerToken = config.get[String]("microservice.services.des.bearer-token")

  private val CORRELATION_ID: String = "X-Correlation-ID"
  private val FORWARDED_HOST: String = "X-Forwarded-Host"
  private val MDTP: String           = "MDTP"

  def submit(declaration: Declaration, isAmendment: Boolean): Future[SubmissionResponse] = {

    implicit val hc: HeaderCarrier = {

      def geCorrelationId(isAmendment: Boolean): String =
        if (isAmendment) declaration.amendCorrelationId.getOrElse(throw new Exception(s"AmendCorrelation Id is empty"))
        else declaration.correlationId

      HeaderCarrier()
        .withExtraHeaders(
          HeaderNames.ACCEPT        -> ContentTypes.JSON,
          HeaderNames.DATE          -> now,
          HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
          CORRELATION_ID            -> geCorrelationId(isAmendment),
          FORWARDED_HOST            -> MDTP
        )
    }

    def getRefinedData(dataOrAmendData: JsObject): JsObject =
      dataOrAmendData.validate(Etmp.formats) match {
        case exception: JsError =>
          logger.error(
            s"[HODConnector][submit] PNGRS_DES_SUBMISSION_FAILURE There is problem with parsing declaration, " +
              s"Parsing failed for this ChargeReference :  ${declaration.chargeReference}, " +
              s"CorrelationId :  ${declaration.correlationId}, Exception : $exception"
          )
          JsObject.empty
        case _                  => Json.toJsObject(dataOrAmendData.as[Etmp])
      }

    def call: Future[SubmissionResponse] =
      if (isAmendment) {
        getRefinedData(declaration.amendData.get) match {
          case returnedJsObject if returnedJsObject.value.isEmpty =>
            Future.successful(SubmissionResponse.ParsingException)
          case returnedJsObject                                   =>
            httpClientV2
              .post(url"$declarationFullUrl")
              .withBody(returnedJsObject)
              .execute[SubmissionResponse]
              .filter(_ != SubmissionResponse.Error)
        }
      } else {
        getRefinedData(declaration.data) match {
          case returnedJsObject if returnedJsObject.value.isEmpty =>
            Future.successful(SubmissionResponse.ParsingException)
          case returnedJsObject                                   =>
            httpClientV2
              .post(url"$declarationFullUrl")
              .withBody(returnedJsObject)
              .execute[SubmissionResponse]
              .filter(_ != SubmissionResponse.Error)
        }
      }

    circuitBreaker
      .withCircuitBreaker(call)
      .fallbackTo(Future.successful(SubmissionResponse.Error))
  }

  implicit val hc: HeaderCarrier = {

    def geCorrelationId(declaration: Declaration, isAmendment: Boolean): String =
      if (isAmendment) declaration.amendCorrelationId.getOrElse(throw new Exception(s"AmendCorrelation Id is empty"))
      else declaration.correlationId

    HeaderCarrier()
      .withExtraHeaders(
        HeaderNames.ACCEPT        -> ContentTypes.JSON,
        HeaderNames.DATE          -> now,
        HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
        CORRELATION_ID            -> "correlationID",
        FORWARDED_HOST            -> MDTP
      )
  }

  def getRefinedData(dataOrAmendData: JsObject): JsObject =
    dataOrAmendData.validate(Etmp.formats) match {
      case exception: JsError =>
        logger.error(
          s"[HODConnector][submit] PNGRS_DES_SUBMISSION_FAILURE There is problem with parsing declaration, " +
            s"Parsing failed for this ChargeReference :  ${}, " +
            s"CorrelationId :  ${}, Exception : $exception"
        )
        JsObject.empty
      case _                  => Json.toJsObject(dataOrAmendData.as[Etmp])
    }

  def call(declaration: Declaration, isAmendment: Boolean): Future[SubmissionResponse] =
    if (isAmendment) {
      getRefinedData(declaration.amendData.get) match {
        case returnedJsObject if returnedJsObject.value.isEmpty =>
          Future.successful(SubmissionResponse.ParsingException)
        case returnedJsObject                                   =>
          httpClientV2
            .post(url"$declarationFullUrl")
            .withBody(returnedJsObject)
            .execute[SubmissionResponse]
            .filter(_ != SubmissionResponse.Error)
      }
    } else {
      getRefinedData(declaration.data) match {
        case returnedJsObject if returnedJsObject.value.isEmpty =>
          Future.successful(SubmissionResponse.ParsingException)
        case returnedJsObject                                   =>
          httpClientV2
            .post(url"$declarationFullUrl")
            .withBody(returnedJsObject)
            .execute[SubmissionResponse]
            .filter(_ != SubmissionResponse.Error)
      }
    }
}
