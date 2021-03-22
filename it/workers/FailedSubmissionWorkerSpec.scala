package workers

import models.declarations.{Declaration, State}
import models.{ChargeReference, Lock}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running
import reactivemongo.play.json.collection.JSONCollection
import repositories.LockRepository
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FailedSubmissionWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a failed submission worker" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "must lock failed records when it processes them" in {

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj()),
        Declaration(ChargeReference(1), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj()),
        Declaration(ChargeReference(2), State.PendingPayment, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .many(declarations)
      }.futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[FailedSubmissionWorker]

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.isLocked(0).futureValue mustEqual true
        lockRepository.isLocked(1).futureValue mustEqual true
        lockRepository.isLocked(2).futureValue mustEqual false
      }
    }

    "must not process locked records" in {

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj()),
        Declaration(ChargeReference(1), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .many(declarations)
      }.futureValue

      database.flatMap {
        _.collection[JSONCollection]("locks")
          .insert(ordered = true)
          .one(Lock(0))
      }.futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[FailedSubmissionWorker]

        val declaration = worker.tap.pull.futureValue.value
        declaration.chargeReference.value mustEqual 1
      }
    }

    "must set failed records to have a status of Paid" in {

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj()),
        Declaration(ChargeReference(1), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .many(declarations)
      }.futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[FailedSubmissionWorker]

        val declaration = worker.tap.pull.futureValue.value
        declaration.chargeReference.value mustEqual 0
        declaration.state mustEqual State.Paid
      }
    }

    "must continue processing after a transient failure acquiring a lock" in {

      import play.api.inject._

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj()),
        Declaration(ChargeReference(1), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .many(declarations)
      }.futureValue

      val mockLockRepository = mock[LockRepository]

      when(mockLockRepository.started) thenReturn Future.successful(())

      when(mockLockRepository.lock(any()))
        .thenReturn(Future.failed(new Exception))
        .thenReturn(Future.successful(true))

      val app = builder.overrides(bind[LockRepository].toInstance(mockLockRepository)).build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[FailedSubmissionWorker]

        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(1)
      }
    }

    "must complete when all failed declarations have been processed" in {

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj()),
        Declaration(ChargeReference(1), State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .many(declarations)
      }.futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[FailedSubmissionWorker]

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        worker.tap.pull.futureValue must not be defined
      }
    }
  }
}
