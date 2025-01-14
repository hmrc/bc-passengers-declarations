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

import com.github.tomakehurst.wiremock.client.WireMock.any as _
import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase
import metrics.MetricsOperator
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus}
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.model.Filters
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.test.Helpers.*
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import utils.WireMockHelper
import org.mongodb.scala.SingleObservableFuture

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class MetricsWorkerISpec
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

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.metrics-worker.interval" -> "1 second"
    )

  val declarationsStatusReads: Reads[DeclarationsStatus] = (
    (JsPath \ "counters" \ "pending-payment-counter" \ "count").read[Int] and
      (JsPath \ "counters" \ "payment-complete-counter" \ "count").read[Int] and
      (JsPath \ "counters" \ "payment-failed-counter" \ "count").read[Int] and
      (JsPath \ "counters" \ "payment-cancelled-counter" \ "count").read[Int] and
      (JsPath \ "counters" \ "failed-submission-counter" \ "count").read[Int]
  )(DeclarationsStatus.apply)

  "A metrics worker" should {

    "must update metrics to match the current collection state" in {

      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val declarations = List(
        Declaration(
          ChargeReference(0),
          State.PendingPayment,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          LocalDateTime.now(ZoneOffset.UTC)
        ),
        Declaration(
          ChargeReference(1),
          State.PaymentFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          LocalDateTime.now(ZoneOffset.UTC)
        ),
        Declaration(
          ChargeReference(2),
          State.Paid,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          LocalDateTime.now(ZoneOffset.UTC)
        ),
        Declaration(
          ChargeReference(3),
          State.PaymentCancelled,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          LocalDateTime.now(ZoneOffset.UTC)
        ),
        Declaration(
          ChargeReference(4),
          State.SubmissionFailed,
          None,
          sentToEtmp = false,
          None,
          correlationId,
          None,
          Json.obj(),
          Json.obj(),
          None,
          LocalDateTime.now(ZoneOffset.UTC)
        )
      )

      await(repository.collection.insertMany(declarations).toFuture())

      val app = builder.build()

      running(app) {

        val metricsOperator = app.injector.instanceOf[MetricsOperator]

        val worker = new MetricsWorker(
          repository.asInstanceOf[DeclarationsRepository],
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          metricsOperator
        )

        worker.tap.pull().futureValue shouldBe Some(DeclarationsStatus(1, 1, 1, 1, 1))

      }
    }
  }
}
