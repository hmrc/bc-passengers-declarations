package connectors

import com.google.inject.{Inject, Singleton}
import models.{Declaration, Service}
import play.api.Configuration
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
                               http: HttpClient,
                               config: Configuration
                             )(implicit ec: ExecutionContext) {

  private val baseUrl = config.get[Service]("microservice.services.des")

  def submit(declaration: Declaration)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.POST[Declaration, HttpResponse](s"$baseUrl/declarations/passengerdeclaration/v1", declaration)
}
