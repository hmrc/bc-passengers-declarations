
package workers

import akka.stream.Materializer

import java.time.{LocalDateTime, ZoneOffset}
import com.github.tomakehurst.wiremock.client.WireMock.{any => _}
import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase
import metrics.MetricsOperator
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{ JsPath, Json, Reads}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.WireMockHelper
import scala.concurrent.ExecutionContext.Implicits.global

class MetricsWorkerSpec extends IntegrationSpecCommonBase with WireMockHelper with DefaultPlayMongoRepositorySupport[Declaration] {

  val validationService: ValidationService = app.injector.instanceOf[ValidationService]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  val chargeReferenceService: ChargeReferenceService = app.injector.instanceOf[ChargeReferenceService]

  override def repository = new DefaultDeclarationsRepository(mongoComponent,
    chargeReferenceService,
    validationService,
    Configuration(ConfigFactory.load(System.getProperty("config.resource")))
  )

  def lockRepository = new DefaultLockRepository(mongoComponent)

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    await(repository.collection.drop().toFuture())
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.collection.drop().toFuture())
  }

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.metrics-worker.interval" -> "1 second"
    )

  val declarationsStatusReads: Reads[DeclarationsStatus] = (
    (JsPath \ "counters" \ "pending-payment-counter" \ "count").read[Int] and
    (JsPath \ "counters" \ "payment-complete-counter" \ "count").read[Int] and
    (JsPath \ "counters" \"payment-failed-counter" \ "count").read[Int] and
    (JsPath \ "counters" \ "payment-cancelled-counter" \ "count").read[Int] and
    (JsPath \ "counters" \ "failed-submission-counter" \ "count").read[Int]
  )(DeclarationsStatus.apply _)

  "A metrics worker" should  {

    "must update metrics to match the current collection state" in {

      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

      await(repository.collection.drop().toFuture())
      await(lockRepository.collection.drop().toFuture())


      val declarations =  List(
        Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp = false, None, correlationId, None,Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
        Declaration(ChargeReference(1), State.PaymentFailed, None, sentToEtmp = false, None, correlationId, None,Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
        Declaration(ChargeReference(2), State.Paid, None, sentToEtmp = false, None, correlationId, None,Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
        Declaration(ChargeReference(3), State.PaymentCancelled, None, sentToEtmp = false, None, correlationId, None,Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
        Declaration(ChargeReference(4), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, None,Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC))

      )


      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {


        val metricsOperator = app.injector.instanceOf[MetricsOperator]

        val worker = new MetricsWorker(repository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))), metricsOperator)

        worker.tap.pull.futureValue mustBe Some(DeclarationsStatus(1, 1, 1, 1, 1))

        val r1 = route(app, FakeRequest("GET", "/admin/metrics")).get
        r1.futureValue
        Json.fromJson[DeclarationsStatus](contentAsJson(r1))(declarationsStatusReads).get mustBe DeclarationsStatus(1, 1, 1, 1, 1)
      }
    }
  }
}

