package workers

import java.time.{LocalDateTime, ZoneOffset}
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
import play.api.libs.json.Json
import play.api.test.Helpers.running
import reactivemongo.api.Cursor
import reactivemongo.play.json.JsObjectDocumentWriter
import reactivemongo.play.json.collection._
import repositories.LockRepository
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

class AmendmentPaymentTimeoutWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
          .configure(
            "declarations.payment-no-response-timeout" -> "1 minute",
            "workers.amendment-payment-timeout-worker.interval" -> "1 second"
          )

  "An amendment payment timeout worker" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    val amendCorrelationId = "ge28db96-d9db-4220-9e12-f2d267267c30"

    "must log stale declarations" in {

      database.flatMap(_.drop()).futureValue

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PaymentFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, Some(State.PaymentCancelled), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(3), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(4), State.Paid, Some(State.PaymentFailed), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[AmendmentPaymentTimeoutWorker]

        TestLoggerAppender.queue.dequeueAll(_ => true)

        val staleDeclarations = List(
          worker.tap.pull.futureValue.value,
          worker.tap.pull.futureValue.value,
          worker.tap.pull.futureValue.value
        )

        staleDeclarations.map(_.chargeReference) must contain allOf (ChargeReference(0), ChargeReference(1), ChargeReference(2))
        staleDeclarations.map(_.amendState) must contain allOf (Some(State.PendingPayment), Some(State.PaymentFailed), Some(State.PaymentCancelled))

        val logEvents = List(
          TestLoggerAppender.queue.dequeue(),
          TestLoggerAppender.queue.dequeue(),
          TestLoggerAppender.queue.dequeue()
        )

        logEvents.map(_.getMessage) must contain allOf ("Declaration 2 is stale, deleting", "Declaration 1 is stale, deleting" , "Declaration 0 is stale, deleting")


        val remaining = database.flatMap {
          _.collection[JSONCollection]("declarations")
            .find(Json.obj(), None)
            .cursor[Declaration]()
            .collect[List](10, Cursor.ContOnError())
        }.futureValue

        remaining mustEqual declarations.filter(_.chargeReference.value > 2)
      }
    }

    "must not log locked stale records" in {

      database.flatMap(_.drop()).futureValue

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PendingPayment), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, Some(State.PendingPayment), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.lock(0)

        val worker = app.injector.instanceOf[AmendmentPaymentTimeoutWorker]

        val declaration = worker.tap.pull.futureValue.value
        declaration.chargeReference.value mustEqual 1

      }
    }

    "must lock stale records when it processes them" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PaymentFailed), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, Some(State.PaymentCancelled), sentToEtmp = false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[AmendmentPaymentTimeoutWorker]

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.isLocked(0).futureValue mustEqual true
        lockRepository.isLocked(1).futureValue mustEqual true
        lockRepository.isLocked(2).futureValue mustEqual false
      }
    }

    "must continue to process data" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[AmendmentPaymentTimeoutWorker]

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PaymentCancelled), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
          Declaration(ChargeReference(2), State.Paid, Some(State.PaymentFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
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
        Declaration(ChargeReference(0), State.Paid, Some(State.PaymentCancelled), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
        Declaration(ChargeReference(1), State.Paid, Some(State.PaymentFailed), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))
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

        val worker = app.injector.instanceOf[AmendmentPaymentTimeoutWorker]

        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(1)
        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(0)

      }
    }

    "must continue processing after a transient failure getting stale declarations" in {

      database.flatMap(_.drop()).futureValue

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .one(Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)))
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

          val worker = app.injector.instanceOf[AmendmentPaymentTimeoutWorker]

          worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(0)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert(ordered = true)
              .one(Declaration(ChargeReference(1), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)))
          }.futureValue

          proxy.open()

          worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(1)
        }
      } finally {

        proxy.close()
        reactor.close()
      }
    }
  }
}
