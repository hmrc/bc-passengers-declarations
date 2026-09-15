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
import models.{HipSubmissionResponse, Response, Service}
import org.apache.pekko.pattern.CircuitBreaker
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import play.api.i18n.Lang.logger.logger
import play.api.libs.json.{JsError, JsObject, Json}
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import java.time.Instant
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class HipConnector @Inject() (
  httpClientV2: HttpClientV2,
  config: Configuration,
  @Named("des") circuitBreaker: CircuitBreaker
)(implicit ec: ExecutionContext) {

  private val hipBaseUrl            = config.get[Service]("microservice.services.des.hip")
  private val hipSubmissionUrl      = config.get[String]("microservice.services.des.hip.submissionUrl")
  private val hipDeclarationFullUrl = s"$hipBaseUrl/$hipSubmissionUrl"

  private val hipClientId     = config.get[String]("microservice.services.des.hip.client-id")
  private val hipClientSecret = config.get[String]("microservice.services.des.hip.client-secret")
  private val hipAuthorizationToken: String =
    Base64.getEncoder.encodeToString(s"$hipClientId:$hipClientSecret".getBytes("UTF-8"))

  private val MDTP: String = "MDTP"

  private val HIP_CORRELATION_ID: String      = "correlationid"
  private val HIP_MESSAGE_TYPE: String        = "X-Message-Type"
  private val HIP_ORIGINATING_SYSTEM: String  = "X-Originating-System"
  private val HIP_RECEIPT_DATE: String        = "X-Receipt-Date"
  private val HIP_REGIME_TYPE: String         = "X-Regime-Type"
  private val HIP_TRANSMITTING_SYSTEM: String = "X-Transmitting-System"

  private def getCorrelationId(declaration: Declaration, isAmendment: Boolean): String =
    if (isAmendment) declaration.amendCorrelationId.getOrElse(throw new Exception(s"AmendCorrelation Id is empty"))
    else declaration.correlationId

  /**
   * EPID1778 requires "correlationid" to conform to the standard 36-character UUID format.
   * Our stored correlationId originates from bc-passengers-frontend and isn't guaranteed to be
   * one, so validate it and fall back to a freshly generated UUID rather than sending a value
   * HIP will reject.
   */
  private def getHIPCorrelationId(candidate: String): String =
    try {
      UUID.fromString(candidate)
      candidate
    } catch {
      case _: IllegalArgumentException =>
        logger.warn(
          s"[HipConnector][getHIPCorrelationId] '$candidate' is not a valid UUID - generating a replacement"
        )
        UUID.randomUUID().toString
    }

  def submit(declaration: Declaration, isAmendment: Boolean): Future[Response] = {

    val dataToSubmit = if (isAmendment) declaration.amendData.get else declaration.data
    val parsedEtmp   = dataToSubmit.validate(Etmp.formats)

    def requestParameter(paramName: String): Option[String] =
      parsedEtmp.asOpt
        .flatMap(_.simpleDeclarationRequest.requestCommon.requestParameters.find(_.paramName == paramName))
        .map(_.paramValue)

    val regime = requestParameter("REGIME").getOrElse("PNGR")

    def getRefinedHipData: JsObject =
      parsedEtmp match {
        case exception: JsError =>
          logger.error(
            s"[HipConnector][submit] PNGRS_DES_SUBMISSION_FAILURE There is problem with parsing declaration, " +
              s"Parsing failed for this ChargeReference: ${declaration.chargeReference}, " +
              s"CorrelationId: ${declaration.correlationId}, Exception: $exception"
          )
          JsObject.empty
        case etmp                =>
          try Json.toJsObject(EtmpHipTransformer.transform(etmp.get))
          catch {
            case NonFatal(e) =>
              logger.error(
                s"[HipConnector][submit] PNGRS_DES_SUBMISSION_FAILURE HIP transform failed for ChargeReference: " +
                  s"${declaration.chargeReference}, CorrelationId: ${declaration.correlationId}, Exception: $e"
              )
              JsObject.empty
          }
      }

    val headers: Seq[(String, String)] =
      Seq(
        HeaderNames.ACCEPT        -> ContentTypes.JSON,
        HeaderNames.CONTENT_TYPE  -> ContentTypes.JSON,
        HeaderNames.AUTHORIZATION -> s"Basic $hipAuthorizationToken",
        HIP_CORRELATION_ID        -> getHIPCorrelationId(getCorrelationId(declaration, isAmendment)),
        HIP_MESSAGE_TYPE          -> (if (isAmendment) "DeclarationAmend" else "DeclarationCreate"),
        HIP_ORIGINATING_SYSTEM    -> MDTP,
        HIP_RECEIPT_DATE          -> Instant.now().toString,
        HIP_REGIME_TYPE           -> regime,
        HIP_TRANSMITTING_SYSTEM   -> "HIP"
      )

    implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(headers*)

    def call: Future[Response] =
      getRefinedHipData match {
        case returnedJsObject if returnedJsObject.value.isEmpty =>
          Future.successful(HipSubmissionResponse.ParsingException)
        case returnedJsObject                                   =>
          httpClientV2
            .post(url"$hipDeclarationFullUrl")
            .withBody(returnedJsObject)
            .execute[HipSubmissionResponse]
            .filter(_ != HipSubmissionResponse.Error)
      }

    circuitBreaker
      .withCircuitBreaker(call)
      .fallbackTo(Future.successful(HipSubmissionResponse.Error))
  }
}
