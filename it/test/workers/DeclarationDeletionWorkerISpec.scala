/*
 * Copyright 2025 HM Revenue & Customs
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

import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase
import logger.TestLoggerAppender
import models.ChargeReference
import models.declarations.{Declaration, State}
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.model.Filters
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.*
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.mongodb.scala.SingleObservableFuture

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class DeclarationDeletionWorkerISpec
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
        "workers.declaration-deletion-worker.interval"   -> "1 second",
        "workers.declaration-deletion-worker.timeToHold" -> "1 minute"
      )

  private val journeyData: JsObject = Json.obj(
    "euCountryCheck"        -> "greatBritain",
    "arrivingNICheck"       -> true,
    "isUKResident"          -> false,
    "bringingOverAllowance" -> true
  )

  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
  private val dateTimeInPast                   =
    ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(3).format(dateFormatter)
  private val dateTime                         = ZonedDateTime.now(ZoneOffset.UTC).format(dateFormatter)

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
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      TestLoggerAppender.queue.dequeueAll(_ => true)

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = true,
            None,
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        await(lockRepository.lock(0))

        val worker = new DeclarationDeletionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        val declaration = worker.tap.pull().futureValue.get
        declaration.chargeReference.value shouldBe 1
      }
    }

    "must lock Paid records when it processes them" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            None,
            sentToEtmp = true,
            None,
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            None,
            sentToEtmp = true,
            None,
            correlationId,
            None,
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            amendState = Some(State.Paid),
            sentToEtmp = true,
            amendSentToEtmp = Some(true),
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(4),
            State.Paid,
            amendState = Some(State.PendingPayment),
            sentToEtmp = true,
            amendSentToEtmp = Some(false),
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val worker = new DeclarationDeletionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        worker.tap.pull().futureValue
        worker.tap.pull().futureValue

        lockRepository.isLocked(0).futureValue shouldBe true
        lockRepository.isLocked(1).futureValue shouldBe false
        lockRepository.isLocked(2).futureValue shouldBe false
        lockRepository.isLocked(3).futureValue shouldBe true
        lockRepository.isLocked(4).futureValue shouldBe false
      }
    }

    "must continue to process data" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            None,
            sentToEtmp = true,
            None,
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            None,
            sentToEtmp = true,
            None,
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.Paid),
            true,
            Some(true),
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            Some(State.Paid),
            true,
            Some(false),
            correlationId,
            None,
            journeyData,
            dataInPast,
            None,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val worker = new DeclarationDeletionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        worker.tap.pull().futureValue
        worker.tap.pull().futureValue
        worker.tap.pull().futureValue
      }
    }
  }
}
