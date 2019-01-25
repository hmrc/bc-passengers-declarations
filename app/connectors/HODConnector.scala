package connectors

import akka.pattern.CircuitBreaker
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import models.declarations.Declaration
import models.declarations.State.SubmissionFailed
import models.{Service, SubmissionResponse}
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.JsObject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import util.DeclarationDataTransformers

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
  http: HttpClient,
  config: Configuration,
  declarationDataTransformers: DeclarationDataTransformers,
  @Named("des") circuitBreaker: CircuitBreaker
  )(implicit ec: ExecutionContext) extends HttpDate {

  private val baseUrl = config.get[Service]("microservice.services.des")

  private val bearerToken = config.get[String]("microservice.services.des.bearer-token")

  private val CORRELATION_ID: String = "X-Correlation-ID"
  private val FORWARDED_HOST: String = "X-Forwarded-Host"
  private val MDTP: String = "MDTP"

  def submit(declaration: Declaration): Future[SubmissionResponse] = {

    implicit val hc: HeaderCarrier = {

      HeaderCarrier()
        .withExtraHeaders(
          HeaderNames.ACCEPT -> ContentTypes.JSON,
          HeaderNames.DATE -> now,
          HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken",
          CORRELATION_ID -> declaration.correlationId,
          FORWARDED_HOST -> MDTP)
    }

    val oldStyleDeclaration = declarationDataTransformers.declarationToV110(declaration).getOrElse(declaration)

    def call: Future[SubmissionResponse] = {
      http.POST[JsObject, SubmissionResponse](s"$baseUrl/declarations/passengerdeclaration/v1", oldStyleDeclaration.data).filter(_ != SubmissionResponse.Error) flatMap {
        case SubmissionResponse.Failed =>
          http.POST[JsObject, SubmissionResponse](s"$baseUrl/declarations/passengerdeclaration/v1", declaration.data).filter(_ != SubmissionResponse.Error)
        case x => Future.successful(x)
      }
    }

    circuitBreaker.withCircuitBreaker(call)
      .fallbackTo(Future.successful(SubmissionResponse.Error))
  }
}
