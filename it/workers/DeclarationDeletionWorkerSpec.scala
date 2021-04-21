package workers

import java.time.LocalDateTime

import logger.TestLoggerAppender
import models.ChargeReference
import models.declarations.{Declaration, State}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.netcrusher.core.reactor.NioReactor
import org.netcrusher.tcp.TcpCrusherBuilder
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.running
import reactivemongo.play.json.collection._
import repositories.LockRepository
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

class DeclarationDeletionWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar {

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

  "A declaration deletion  worker" - {

    val correlationId = "fk28db96-d9db-4110-9e12-f2d268541c29"


    "must not log locked UnPaid records" in {

      database.flatMap(_.drop()).futureValue

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp = false, None, correlationId, None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, None, sentToEtmp = true, None, correlationId, None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.PendingPayment, None, sentToEtmp = false, None, correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.lock(0)

        val worker = app.injector.instanceOf[DeclarationDeletionWorker]

        val declaration = worker.tap.pull.futureValue.value
        declaration.chargeReference.value mustEqual 1
      }
    }

    "must lock Paid records when it processes them" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, None, sentToEtmp = true, None,correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, None,sentToEtmp = false, None, correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, None,sentToEtmp = true, None, correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(3), State.Paid, amendState = Some(State.Paid), sentToEtmp = true, amendSentToEtmp = Some(true), correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(4), State.Paid, amendState = Some(State.PendingPayment), sentToEtmp = true, amendSentToEtmp = Some(false), correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[DeclarationDeletionWorker]

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.isLocked(0).futureValue mustEqual true
        lockRepository.isLocked(1).futureValue mustEqual false
        lockRepository.isLocked(2).futureValue mustEqual false
        lockRepository.isLocked(3).futureValue mustEqual true
        lockRepository.isLocked(4).futureValue mustEqual false
      }
    }

    "must continue to process data" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[DeclarationDeletionWorker]

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, None,sentToEtmp = true, None, correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, None,sentToEtmp = true, None, correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, Some(State.Paid), true, Some(true), correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(3), State.Paid, Some(State.Paid), true, Some(false), correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue
        worker.tap.pull.futureValue
      }
    }

    "must continue processing after a transient failure acquiring a lock" in {

      import play.api.inject._

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.Paid, None,sentToEtmp = true, None, correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
        Declaration(ChargeReference(1), State.Paid, None,sentToEtmp = true, None, correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
        Declaration(ChargeReference(2), State.Paid, Some(State.Paid),sentToEtmp = true, Some(true), correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
        Declaration(ChargeReference(3), State.Paid, Some(State.PendingPayment),sentToEtmp = true, None, correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5))
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

        val worker = app.injector.instanceOf[DeclarationDeletionWorker]

        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(1)
        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(2)
        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(0)
      }
    }

    "must continue processing after a transient failure getting Paid declarations" in {

      database.flatMap(_.drop()).futureValue

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .one(Declaration(ChargeReference(0), State.Paid, None, sentToEtmp = true, None, correlationId,None,journeyData, Json.obj(), None, LocalDateTime.now.minusMinutes(5)))
      }.futureValue

      val reactor = new NioReactor()

      val proxy = TcpCrusherBuilder.builder()
        .withReactor(reactor)
        .withBindAddress("localhost", 27018)
        .withConnectAddress("localhost", 27017)
        .buildAndOpen()

      val app = builder.configure(
        "mongodb.uri" -> s"mongodb://localhost:${proxy.getBindAddress.getPort}/bc-passengers-declarations-integration"
      ).build()

      try {

        running(app) {

          started(app).futureValue

          val worker = app.injector.instanceOf[DeclarationDeletionWorker]

          worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(0)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert(ordered = true)
              .one(Declaration(ChargeReference(1), State.Paid, None, sentToEtmp = true, None, correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)))
          }.futureValue

          proxy.open()

          worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(1)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert(ordered = true)
              .one(Declaration(ChargeReference(2), State.Paid, Some(State.Paid), sentToEtmp = true, Some(true), correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)))
          }.futureValue

          proxy.open()

          worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(2)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert(ordered = true)
              .one(Declaration(ChargeReference(3), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, None,journeyData,Json.obj(), None, LocalDateTime.now.minusMinutes(5)))
          }.futureValue

          proxy.open()

        }
      } finally {

        proxy.close()
        reactor.close()
      }
    }
  }
}
