/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package connectors

import akka.pattern.CircuitBreaker
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import models.declarations.{Declaration, Etmp}
import models.{Service, SubmissionResponse}
import play.api.{Configuration, Logger}
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{JsError, JsObject, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
  http: HttpClient,
  config: Configuration,
  @Named("des") circuitBreaker: CircuitBreaker
  )(implicit ec: ExecutionContext) extends HttpDate {

  private val baseUrl = config.get[Service]("microservice.services.des")

  private val bearerToken = config.get[String]("microservice.services.des.bearer-token")

  private val CORRELATION_ID: String = "X-Correlation-ID"
  private val FORWARDED_HOST: String = "X-Forwarded-Host"
  private val MDTP: String = "MDTP"

  def submit(declaration: Declaration, isAmendment: Boolean): Future[SubmissionResponse] = {

    implicit val hc: HeaderCarrier = {

      def geCorrelationId(isAmendment: Boolean) : String = {
        if (isAmendment) declaration.amendCorrelationId.getOrElse(throw new Exception(s"AmendCorrelation Id is empty")) else declaration.correlationId
      }

      HeaderCarrier()
        .withExtraHeaders(
          HeaderNames.ACCEPT -> ContentTypes.JSON,
          HeaderNames.DATE -> now,
          HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
          CORRELATION_ID -> geCorrelationId(isAmendment),
          FORWARDED_HOST -> MDTP)
    }

    def getRefinedData (dataOrAmendData: JsObject): JsObject = {
      dataOrAmendData.validate(Etmp.formats) match {
        case exception : JsError => Logger.error(s"PNGRS_DES_SUBMISSION_FAILURE  [HODConnector] There is problem with parsing declaration, Parsing failed for this ChargeReference :  ${declaration.chargeReference}, CorrelationId :  ${declaration.correlationId}, Exception : $exception")
          JsObject.empty
        case _ => Json.toJsObject(dataOrAmendData.as[Etmp])
      }
    }

    def call : Future[SubmissionResponse] = {
      if (isAmendment)
        getRefinedData(declaration.amendData.get) match {
          case returnedJsObject if returnedJsObject.value.isEmpty => Future.successful(SubmissionResponse.ParsingException)
          case returnedJsObject => http.POST[JsObject, SubmissionResponse](s"$baseUrl/declarations/passengerdeclaration/v1", returnedJsObject)
            .filter(_ != SubmissionResponse.Error)
        }
      else
        getRefinedData(declaration.data) match {
          case returnedJsObject if returnedJsObject.value.isEmpty => Future.successful(SubmissionResponse.ParsingException)
          case returnedJsObject => http.POST[JsObject, SubmissionResponse](s"$baseUrl/declarations/passengerdeclaration/v1", returnedJsObject)
            .filter(_ != SubmissionResponse.Error)
        }
    }

    circuitBreaker.withCircuitBreaker(call)
      .fallbackTo(Future.successful(SubmissionResponse.Error))
    }
}
