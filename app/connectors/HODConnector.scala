package connectors

import com.google.inject.{Inject, Singleton}
import models.Service
import play.api.Configuration
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HODConnector @Inject() (
                               http: HttpClient,
                               config: Configuration
                             )(implicit ec: ExecutionContext) {

  private val baseUrl = config.get[Service]("microservice.services.des")

  def submit(request: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.POST[JsValue, HttpResponse](s"$baseUrl/declarations/passengerdeclaration/v1", request)
}
