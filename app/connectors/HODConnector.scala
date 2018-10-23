package connectors

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

@Singleton
class HODConnector @Inject() () {

    def submit(request: JsValue)(implicit hc: HeaderCarrier): Future[HttpResponse] = ???
}
