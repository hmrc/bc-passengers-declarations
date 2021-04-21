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
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{JsObject, Json}
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

    def call(isAmend: Boolean): Future[SubmissionResponse] = {


      if (isAmend) {
        val amendData = Json.toJsObject(declaration.amendData.get.as[Etmp])
        http.POST[JsObject, SubmissionResponse](s"$baseUrl/declarations/passengerdeclaration/v1", amendData)
          .filter(_ != SubmissionResponse.Error)
      } else {
        val data = Json.toJsObject(declaration.data.as[Etmp])
        http.POST[JsObject, SubmissionResponse](s"$baseUrl/declarations/passengerdeclaration/v1", data)
          .filter(_ != SubmissionResponse.Error)
      }
    }

    circuitBreaker.withCircuitBreaker(call(isAmendment))
      .fallbackTo(Future.successful(SubmissionResponse.Error))
  }
}
