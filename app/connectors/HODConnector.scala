package connectors

import java.util.UUID

import com.google.inject.{Inject, Singleton}
import models.{Declaration, Service}
import play.api.Configuration
import play.api.http.{ContentTypes, HeaderNames}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
                               http: HttpClient,
                               config: Configuration
                             )(implicit ec: ExecutionContext) extends HttpDate {

  private val baseUrl = config.get[Service]("microservice.services.des")

  private val CORRELATION_ID: String = "X-Correlation-ID"
  private val FORWARDED_HOST: String = "X-Forwarded-Host"
  private val MDTP: String = "MDTP"

  def submit(declaration: Declaration): Future[HttpResponse] = {

    implicit val hc: HeaderCarrier = {

      val correlationId: String = UUID.randomUUID().toString

      HeaderCarrier()
        .withExtraHeaders(
          HeaderNames.ACCEPT -> ContentTypes.JSON,
          HeaderNames.DATE -> now,
          CORRELATION_ID -> correlationId,
          FORWARDED_HOST -> MDTP)
    }

    http.POST[Declaration, HttpResponse](s"$baseUrl/declarations/passengerdeclaration/v1", declaration)
  }
}
