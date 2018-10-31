package workers

import java.time.LocalDateTime

import ch.qos.logback.classic.Level
import logger.TestLoggerAppender
import models.{ChargeReference, Declaration}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running
import reactivemongo.play.json.collection.JSONCollection
import repositories.LockRepository
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global

class PaymentTimeoutWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
          .configure(
            "declarations.payment-no-response-timeout" -> "1 minute",
            "workers.payment-timeout-worker.interval" -> "1 second"
          )

  "A payment timeout worker" - {

    "must log stale declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference("0"), Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference("1"), Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference("2"), Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

        val declaration = worker.tap.pull.futureValue.value

        val logEvent = TestLoggerAppender.queue.dequeue()

        logEvent.getLevel mustEqual Level.INFO
        logEvent.getMessage mustEqual "Declaration 0 is stale"
      }
    }

    "must not log locked stale records" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference("0"), Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference("1"), Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference("2"), Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.lock("0")

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

        val declaration = worker.tap.pull.futureValue.value
        declaration.chargeReference.value mustEqual "1"
      }
    }

    "must lock stale records when it processes them" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference("0"), Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference("1"), Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference("2"), Json.obj(), LocalDateTime.now)
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

        lockRepository.isLocked("0").futureValue mustEqual true
        lockRepository.isLocked("1").futureValue mustEqual true
        lockRepository.isLocked("2").futureValue mustEqual false
      }
    }

    "must continue to process data" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[PaymentTimeoutWorker]

        val declarations = List(
          Declaration(ChargeReference("0"), Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference("1"), Json.obj(), LocalDateTime.now.minusMinutes(5))
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
  }
}
