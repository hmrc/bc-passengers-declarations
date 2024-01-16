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
import com.github.tomakehurst.wiremock.client.WireMock.{any => _}
import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase
import models.declarations.{Declaration, State}
import models.{ChargeReference, Lock}
import org.mongodb.scala.model.Filters
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global

class FailedSubmissionWorkerISpec
    extends IntegrationSpecCommonBase
    with WireMockHelper
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

  "a failed submission worker" should {
    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "must lock failed records when it processes them" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      val declarations = List(
        Declaration(
          ChargeReference(0),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        ),
        Declaration(
          ChargeReference(1),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        ),
        Declaration(
          ChargeReference(2),
          State.PendingPayment,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        )
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {

        val worker = new FailedSubmissionWorker(
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

    "must not process locked records" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      val declarations = List(
        Declaration(
          ChargeReference(0),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        ),
        Declaration(
          ChargeReference(1),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        )
      )

      await(repository.collection.insertMany(declarations).toFuture())

      await(lockRepository.collection.insertOne(Lock(0)).toFuture())

      val app = builder.build()

      running(app) {

        val worker = new FailedSubmissionWorker(
          repository,
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        val declaration = worker.tap.pull().futureValue.get
        declaration.chargeReference.value mustEqual 1
      }
    }

    "must set failed records to have a status of Paid" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      val declarations = List(
        Declaration(
          ChargeReference(0),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        ),
        Declaration(
          ChargeReference(1),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        )
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {

        val worker = new FailedSubmissionWorker(
          repository,
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        val declaration = worker.tap.pull().futureValue.get
        declaration.chargeReference.value mustEqual 0
        declaration.state mustEqual State.Paid
      }
    }

    "must complete when all failed declarations have been processed" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      val declarations = List(
        Declaration(
          ChargeReference(0),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        ),
        Declaration(
          ChargeReference(1),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj()
        )
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {

        val worker = new FailedSubmissionWorker(
          repository,
          lockRepository,
          Configuration(ConfigFactory.load(System.getProperty("config.resource")))
        )

        worker.tap.pull().futureValue
        worker.tap.pull().futureValue

        worker.tap.pull().futureValue must not be defined
      }
    }
  }
}
