package workers


import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase

import java.time.{LocalDateTime, ZoneOffset}
import logger.TestLoggerAppender
import models.ChargeReference
import models.declarations.{Declaration, State}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{await, running}
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.Helpers._
import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper

class DeclarationDeletionWorkerSpec extends IntegrationSpecCommonBase with DefaultPlayMongoRepositorySupport[Declaration] {

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

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "workers.declaration-deletion-worker.interval" -> "1 second",
        "workers.declaration-deletion-worker.timeToHold" -> "1 minute"
      )

  private val journeyData: JsObject = Json.obj(
    "euCountryCheck" -> "greatBritain",
    "arrivingNICheck" -> true,
    "isUKResident" -> false,
    "bringingOverAllowance" -> true)

  private val dateTimeInPast = DateTime.now().minusMinutes(5).withZone(DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss'Z'")
  private val dateTime = DateTime.now().withZone(DateTimeZone.UTC).toString("yyyy-MM-dd'T'HH:mm:ss'Z'")

  private val dataInPast: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> dateTimeInPast
      )
    )
  )

  private val data: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> dateTime
      )
    )
  )

  "A declaration deletion  worker" should {

    val correlationId = "fk28db96-d9db-4110-9e12-f2d268541c29"


    "must not log locked UnPaid records" in {

      await(repository.collection.drop().toFuture())
      await(lockRepository.collection.drop().toFuture())

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp = false, None, correlationId, None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, None, sentToEtmp = true, None, correlationId, None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(2), State.PendingPayment, None, sentToEtmp = false, None, correlationId,None,journeyData, data, None, LocalDateTime.now(ZoneOffset.UTC))
        )

        await(repository.collection.insertMany(declarations).toFuture())

        lockRepository.lock(0)

        val worker = new DeclarationDeletionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

        val declaration = worker.tap.pull.futureValue.get
        declaration.chargeReference.value mustEqual 1
      }
    }

    "must lock Paid records when it processes them" in {

      await(repository.collection.drop().toFuture())
      await(lockRepository.collection.drop().toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, None, sentToEtmp = true, None,correlationId, None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, None,sentToEtmp = false, None, correlationId, None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, None,sentToEtmp = true, None, correlationId,None,journeyData, data, None, LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(3), State.Paid, amendState = Some(State.Paid), sentToEtmp = true, amendSentToEtmp = Some(true), correlationId,None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(4), State.Paid, amendState = Some(State.PendingPayment), sentToEtmp = true, amendSentToEtmp = Some(false), correlationId,None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val worker = new DeclarationDeletionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue



        lockRepository.isLocked(0).futureValue mustEqual true
        lockRepository.isLocked(1).futureValue mustEqual false
        lockRepository.isLocked(2).futureValue mustEqual false
        lockRepository.isLocked(3).futureValue mustEqual true
        lockRepository.isLocked(4).futureValue mustEqual false
      }
    }

      "must continue to process data" in {

        await(repository.collection.drop().toFuture())
        await(lockRepository.collection.drop().toFuture())

        val app = builder.build()

        running(app) {


          val declarations = List(
            Declaration(ChargeReference(0), State.Paid, None,sentToEtmp = true, None, correlationId,None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
            Declaration(ChargeReference(1), State.Paid, None,sentToEtmp = true, None, correlationId,None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
            Declaration(ChargeReference(2), State.Paid, Some(State.Paid), true, Some(true), correlationId,None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
            Declaration(ChargeReference(3), State.Paid, Some(State.Paid), true, Some(false), correlationId,None,journeyData, dataInPast, None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
          )

          await(repository.collection.insertMany(declarations).toFuture())

          val worker = new DeclarationDeletionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

          worker.tap.pull.futureValue
          worker.tap.pull.futureValue
          worker.tap.pull.futureValue
        }
      }
  }
}

