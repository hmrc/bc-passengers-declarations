/*
 * Copyright 2026 HM Revenue & Customs
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
import models.{CMASubmissionResponse, HipSubmissionResponse, Response, Service, SubmissionResponse}
import org.apache.pekko.pattern.CircuitBreaker
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.{JsError, JsObject, Json}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import play.api.libs.ws.writeableOf_JsValue

import java.time.{Instant, ZoneOffset}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.util.Locale
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class HODConnector @Inject() (
  httpClientV2: HttpClientV2,
  config: Configuration,
  @Named("des") circuitBreaker: CircuitBreaker
)(implicit ec: ExecutionContext)
    extends HttpDate {

  private val baseUrl            = config.get[Service]("microservice.services.des")
  private val declarationFullUrl = s"$baseUrl/declarations/passengerdeclaration/v1"

  private val cmaBaseUrl            = config.get[Service]("microservice.services.des.cma")
  private val cmaDeclarationFullUrl = s"$cmaBaseUrl/passengers/declarations/simpledeclaration/v1"

  private val hipBaseUrl            = config.get[Service]("microservice.services.des.hip")
  private val hipSubmissionUrl      = config.get[String]("microservice.services.des.hip.submissionUrl")
  private val hipDeclarationFullUrl = s"$hipBaseUrl/$hipSubmissionUrl"
  private val hipBearerToken        = config.get[String]("microservice.services.des.hip.bearer-token")

  private lazy val isUsingCMA: Boolean = config.get[Boolean]("feature.isUsingCMA")
  private lazy val isUsingHip: Boolean = config.get[Boolean]("feature.isUsingHip")

  private val bearerToken    = config.get[String]("microservice.services.des.bearer-token")
  private val cmaBearerToken = config.get[String]("microservice.services.des.cma.bearer-token")

  private val CORRELATION_ID: String = "X-Correlation-ID"
  private val FORWARDED_HOST: String = "X-Forwarded-Host"
  private val MDTP: String           = "MDTP"

  private val HIP_CORRELATION_ID: String     = "correlationid"
  private val HIP_MESSAGE_TYPE: String       = "X-Message-Type"
  private val HIP_ORIGINATING_SYSTEM: String = "X-Originating-System"
  private val HIP_RECEIPT_DATE: String       = "X-Receipt-Date"
  private val HIP_REGIME_TYPE: String        = "X-Regime-Type"
  private val HIP_TRANSMITTING_SYSTEM: String = "X-Transmitting-System"

  def submit(declaration: Declaration, isAmendment: Boolean): Future[Response] = {

    implicit val hc: HeaderCarrier = {

      def getCorrelationId(isAmendment: Boolean): String =
        if (isAmendment) declaration.amendCorrelationId.getOrElse(throw new Exception(s"AmendCorrelation Id is empty"))
        else declaration.correlationId

      if (isUsingHip)
        HeaderCarrier()
          .withExtraHeaders(
            HeaderNames.ACCEPT         -> ContentTypes.JSON,
            HeaderNames.CONTENT_TYPE   -> ContentTypes.JSON,
            HeaderNames.AUTHORIZATION  -> s"Bearer $hipBearerToken",
            HIP_CORRELATION_ID         -> getCorrelationId(isAmendment),
            HIP_MESSAGE_TYPE           -> (if (isAmendment) "DeclarationAmend" else "DeclarationCreate"),
            HIP_ORIGINATING_SYSTEM     -> MDTP,
            HIP_RECEIPT_DATE           -> Instant.now().toString,
            HIP_REGIME_TYPE            -> "PNGR",
            HIP_TRANSMITTING_SYSTEM    -> "HIP"
          )
      else if (isUsingCMA)

        val CMANow = HODConnector.dateFormatter.format(Instant.now())

        HeaderCarrier()
          .withExtraHeaders(
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            HeaderNames.CONTENT_TYPE  -> ContentTypes.JSON,
            HeaderNames.DATE          -> CMANow,
            HeaderNames.AUTHORIZATION -> s"Bearer $cmaBearerToken",
            CORRELATION_ID            -> getCorrelationId(isAmendment),
            FORWARDED_HOST            -> MDTP
          )
      else
        HeaderCarrier()
          .withExtraHeaders(
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            HeaderNames.DATE          -> now,
            HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
            CORRELATION_ID            -> getCorrelationId(isAmendment),
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

    def getRefinedHipData(dataOrAmendData: JsObject): JsObject =
      dataOrAmendData.validate(Etmp.formats) match {
        case exception: JsError =>
          logger.error(
            s"[HODConnector][submit] PNGRS_DES_SUBMISSION_FAILURE There is problem with parsing declaration, " +
              s"Parsing failed for this ChargeReference :  ${declaration.chargeReference}, " +
              s"CorrelationId :  ${declaration.correlationId}, Exception : $exception"
          )
          JsObject.empty
        case etmp                =>
          try Json.toJsObject(EtmpHipTransformer.transform(etmp.get))
          catch {
            case NonFatal(e) =>
              logger.error(
                s"[HODConnector][submit] PNGRS_DES_SUBMISSION_FAILURE HIP transform failed for ChargeReference : " +
                  s"${declaration.chargeReference}, CorrelationId : ${declaration.correlationId}, Exception : $e"
              )
              JsObject.empty
          }
      }

    def call: Future[Response] =
      if (isUsingHip) {
        val dataToSubmit = if (isAmendment) declaration.amendData.get else declaration.data
        getRefinedHipData(dataToSubmit) match {
          case returnedJsObject if returnedJsObject.value.isEmpty =>
            Future.successful(HipSubmissionResponse.ParsingException)
          case returnedJsObject                                   =>
            httpClientV2
              .post(url"$hipDeclarationFullUrl")
              .withBody(returnedJsObject)
              .execute[HipSubmissionResponse]
              .filter(_ != HipSubmissionResponse.Error)
        }
      } else if (isUsingCMA) {
        if (isAmendment) {
          getRefinedData(declaration.amendData.get) match {
            case returnedJsObject if returnedJsObject.value.isEmpty =>
              Future.successful(CMASubmissionResponse.ParsingException)
            case returnedJsObject                                   =>
              httpClientV2
                .post(url"$cmaDeclarationFullUrl")
                .withBody(returnedJsObject)
                .execute[CMASubmissionResponse]
                .filter(_ != CMASubmissionResponse.Error)
          }
        } else {
          getRefinedData(declaration.data) match {
            case returnedJsObject if returnedJsObject.value.isEmpty =>
              Future.successful(CMASubmissionResponse.ParsingException)
            case returnedJsObject                                   =>
              httpClientV2
                .post(url"$cmaDeclarationFullUrl")
                .withBody(returnedJsObject)
                .execute[CMASubmissionResponse]
                .filter(_ != CMASubmissionResponse.Error)
          }
        }
      } else {
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

    if (isUsingHip) {
      circuitBreaker
        .withCircuitBreaker(call)
        .fallbackTo(Future.successful(HipSubmissionResponse.Error))
    } else if (isUsingCMA) {
      circuitBreaker
        .withCircuitBreaker(call)
        .fallbackTo(Future.successful(CMASubmissionResponse.Error))
    } else {
      circuitBreaker
        .withCircuitBreaker(call)
        .fallbackTo(Future.successful(SubmissionResponse.Error))
    }

  }
}

private object HODConnector {
  private val dateFormatter: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .appendPattern("EEE, dd MMM uuuu HH:mm:ss 'GMT'")
      .toFormatter(Locale.ENGLISH)
      .withZone(ZoneOffset.UTC)
}
