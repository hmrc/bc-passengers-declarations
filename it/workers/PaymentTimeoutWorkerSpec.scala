package workers

import java.time.LocalDateTime

import ch.qos.logback.classic.Level
import logger.TestLoggerAppender
import models.ChargeReference
import models.declarations.{Declaration, State}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.netcrusher.core.reactor.NioReactor
import org.netcrusher.tcp.TcpCrusherBuilder
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection
import repositories.LockRepository
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class PaymentTimeoutWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
          .configure(
            "declarations.payment-no-response-timeout" -> "1 minute",
            "workers.payment-timeout-worker.interval" -> "1 second"
          )

  "A payment timeout worker" - {

    "must log stale declarations" in {

      database.flatMap(_.drop()).futureValue

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.PendingPayment, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

        worker.tap.pull.futureValue.value

        TestLoggerAppender.queue.dequeue()
        val logEvent = TestLoggerAppender.queue.dequeue()

        logEvent.getLevel mustEqual Level.INFO
        logEvent.getMessage mustEqual "Declaration 0 is stale"
      }
    }

    "must not log locked stale records" in {

      database.flatMap(_.drop()).futureValue

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.PendingPayment, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.lock(0)

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

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
          Declaration(ChargeReference(0), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.PendingPayment, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

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

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue
      }
    }

    "must continue processing after a transient failure acquiring a lock" in {

      import play.api.inject._

      database.flatMap(_.drop()).futureValue

      val declarations = List(
        Declaration(ChargeReference(0), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)),
        Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5))
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert[Declaration](ordered = true)
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

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(1)
        worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(0)
      }
    }

    "must continue processing after a transient failure getting stale declarations" in {

      database.flatMap(_.drop()).futureValue

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert[Declaration](ordered = true)
          .one(Declaration(ChargeReference(0), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)))
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

          val worker = app.injector.instanceOf[PaymentTimeoutWorker]

          worker.tap.pull.futureValue.value.chargeReference mustEqual ChargeReference(0)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert[Declaration](ordered = true)
              .one(Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now.minusMinutes(5)))
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
