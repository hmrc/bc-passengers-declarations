package workers

import com.typesafe.config.ConfigFactory
import models.declarations.{Declaration, State}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.await
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.Helpers._
import akka.stream.Materializer
import helpers.IntegrationSpecCommonBase
import models.{ChargeReference, Lock}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.libs.json.Json
import utils.WireMockHelper


class AmendmentFailedSubmissionWorkerSpec extends IntegrationSpecCommonBase with WireMockHelper with DefaultPlayMongoRepositorySupport[Declaration] {

  val validationService: ValidationService = app.injector.instanceOf[ValidationService]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  val chargeReferenceService: ChargeReferenceService = app.injector.instanceOf[ChargeReferenceService]

  override def repository = new DefaultDeclarationsRepository(mongoComponent,
    chargeReferenceService,
    validationService,
    Configuration(ConfigFactory.load(System.getProperty("config.resource")))
  )

  def lockRepository = new DefaultLockRepository(mongoComponent, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))


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

  "an amendment failed submission worker" should  {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    val amendCorrelationId = "ge28db96-d9db-4220-9e12-f2d267267c30"

    "must lock failed records when it processes them" in {

      await(repository.collection.drop().toFuture())


      val declarations = Seq(
        Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
        Declaration(ChargeReference(1), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
        Declaration(ChargeReference(2), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()))
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {


        val worker = new AmendmentFailedSubmissionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        lockRepository.isLocked(0).futureValue mustEqual true
        lockRepository.isLocked(1).futureValue mustEqual true
        lockRepository.isLocked(2).futureValue mustEqual false
      }
    }

  "must not process locked records" in {

    await(repository.collection.drop().toFuture())
    await(lockRepository.collection.drop().toFuture())

      val declarations = List(
        Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
        Declaration(ChargeReference(1), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()))
      )

      await(repository.collection.insertMany(declarations).toFuture())
      await(lockRepository.collection.insertOne(Lock(0)).toFuture())

      val app = builder.build()

      running(app) {


        val worker = new AmendmentFailedSubmissionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

        val declaration = worker.tap.pull.futureValue.get
        declaration.chargeReference.value mustEqual 1
      }
    }

    "must set failed records to have a status of Paid" in {

      await(repository.collection.drop().toFuture())
      await(lockRepository.collection.drop().toFuture())

      val declarations = List(
        Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
        Declaration(ChargeReference(1), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()))
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {


        val worker = new AmendmentFailedSubmissionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

        val declaration = worker.tap.pull.futureValue.get
        declaration.chargeReference.value mustEqual 0
      }
    }

    "must complete when all failed declarations have been processed" in {

      await(repository.collection.drop().toFuture())
      await(lockRepository.collection.drop().toFuture())

      val declarations = List(
        Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
        Declaration(ChargeReference(1), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()))
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {


        val worker = new AmendmentFailedSubmissionWorker(repository, lockRepository, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        worker.tap.pull.futureValue must not be defined
      }
    }
  }
}


