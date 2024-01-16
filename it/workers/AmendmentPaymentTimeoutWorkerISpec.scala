/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package workers

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase
import logger.TestLoggerAppender
import models.ChargeReference
import models.declarations.{Declaration, State}
import org.mongodb.scala.model.Filters
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class AmendmentPaymentTimeoutWorkerISpec
    extends IntegrationSpecCommonBase
    with DefaultPlayMongoRepositorySupport[Declaration] {

  val validationService: ValidationService           = app.injector.instanceOf[ValidationService]
  implicit val mat: Materializer                     = app.injector.instanceOf[Materializer]
  val chargeReferenceService: ChargeReferenceService = app.injector.instanceOf[ChargeReferenceService]

  override val repository = new DefaultDeclarationsRepository(
    mongoComponent,
    chargeReferenceService,
    validationService,
    Configuration(ConfigFactory.load(System.getProperty("config.resource")))
  )

  val lockRepository: DefaultLockRepository = new DefaultLockRepository(mongoComponent)

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "declarations.payment-no-response-timeout"          -> "1 minute",
        "workers.amendment-payment-timeout-worker.interval" -> "1 second"
      )

  "An amendment payment timeout worker" should {
    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    val amendCorrelationId = "ge28db96-d9db-4220-9e12-f2d267267c30"

    "log stale declarations" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val declarations = List(
        Declaration(
          ChargeReference(0),
          State.Paid,
          Some(State.PendingPayment),
          sentToEtmp = true,
          Some(false),
          correlationId,
          Some(amendCorrelationId),
          Json.obj(),
          Json.obj(),
          Some(Json.obj()),
          LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
        ),
        Declaration(
          ChargeReference(1),
          State.Paid,
          Some(State.PaymentFailed),
          sentToEtmp = true,
          Some(false),
          correlationId,
          Some(amendCorrelationId),
          Json.obj(),
          Json.obj(),
          Some(Json.obj()),
          LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
        ),
        Declaration(
          ChargeReference(2),
          State.Paid,
          Some(State.PaymentCancelled),
          sentToEtmp = false,
          Some(false),
          correlationId,
          Some(amendCorrelationId),
          Json.obj(),
          Json.obj(),
          Some(Json.obj()),
          LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
        ),
        Declaration(
          ChargeReference(3),
          State.Paid,
          Some(State.PendingPayment),
          sentToEtmp = true,
          Some(false),
          correlationId,
          Some(amendCorrelationId),
          Json.obj(),
          Json.obj(),
          Some(Json.obj()),
          LocalDateTime.now(ZoneOffset.UTC)
        ),
        Declaration(
          ChargeReference(4),
          State.Paid,
          Some(State.PaymentFailed),
          sentToEtmp = false,
          Some(false),
          correlationId,
          Some(amendCorrelationId),
          Json.obj(),
          Json.obj(),
          Some(Json.obj()),
          LocalDateTime.now(ZoneOffset.UTC)
        )
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val worker = new AmendmentPaymentTimeoutWorker(
        repository,
        lockRepository,
        Configuration(ConfigFactory.load(System.getProperty("config.resource")))
      )

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val staleDeclarations = List(
        worker.tap.pull().futureValue.get,
        worker.tap.pull().futureValue.get,
        worker.tap.pull().futureValue.get
      )

      staleDeclarations
        .map(_.chargeReference)           must contain.allOf(ChargeReference(0), ChargeReference(1), ChargeReference(2))
      staleDeclarations.map(_.amendState) must contain.allOf(
        Some(State.PendingPayment),
        Some(State.PaymentFailed),
        Some(State.PaymentCancelled)
      )

      val logEvents = List(
        TestLoggerAppender.queue.dequeue(),
        TestLoggerAppender.queue.dequeue(),
        TestLoggerAppender.queue.dequeue()
      )

      logEvents.map(_.getMessage) must contain.allOf(
        "Declaration 2 is stale, deleting",
        "Declaration 1 is stale, deleting",
        "Declaration 0 is stale, deleting"
      )

      val remaining = repository.collection.find().toFuture().map(_.toList).futureValue

      remaining mustEqual declarations.filter(_.chargeReference.value > 2)
    }

    "not log locked stale records" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = false,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = false,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())
        await(lockRepository.lock(0))

        val worker = new AmendmentPaymentTimeoutWorker(
          repository,
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        val declaration = worker.tap.pull().futureValue.get
        declaration.chargeReference.value mustEqual 1

      }
    }

    "must lock stale records when it processes them" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = false,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.PaymentFailed),
            sentToEtmp = false,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.PaymentCancelled),
            sentToEtmp = false,
            Some(false),
            correlationId,
            Some(amendCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val worker = new AmendmentPaymentTimeoutWorker(
          repository,
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        worker.tap.pull().futureValue
        worker.tap.pull().futureValue

        lockRepository.isLocked(0).futureValue mustEqual true
        lockRepository.isLocked(1).futureValue mustEqual true
        lockRepository.isLocked(2).futureValue mustEqual false
      }
    }
  }
}
