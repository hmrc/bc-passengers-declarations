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
import com.github.tomakehurst.wiremock.client.WireMock.{any => _, _}
import com.typesafe.config.ConfigFactory
import connectors.HODConnector
import helpers.IntegrationSpecCommonBase
import models.declarations.{Declaration, State}
import models.{ChargeReference, SubmissionResponse}
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditingTools
import utils.WireMockHelper
import utils.WireMockUtils.WireMockServerImprovements

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class AmendmentSubmissionWorkerISpec
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
      "workers.amendment-submission-worker.interval" -> "1 second",
      "microservice.services.des.port"               -> server.port(),
      "auditing.consumer.baseUri.port"               -> server.port(),
      "auditing.enabled"                             -> "true"
    )

  val data: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference"  -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"    -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails"     -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader"  -> Json.obj(
          "chargeReference"       -> "XJPR5768524625",
          "portOfEntry"           -> "LHR",
          "portOfEntryName"       -> "Heathrow Airport",
          "expectedDateOfArrival" -> "2018-05-31",
          "timeOfEntry"           -> "13:20",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate"),
          "travellingFrom"        -> "NON_EU Only",
          "onwardTravelGBNI"      -> "GB",
          "uccRelief"             -> false,
          "ukVATPaid"             -> false,
          "ukExcisePaid"          -> false
        ),
        "declarationTobacco" -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationAlcohol" -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            )
          )
        ),
        "declarationOther"   -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        ),
        "liabilityDetails"   -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        )
      )
    )
  )

  val amendData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate"              -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters"        -> Json.arr(
          Json.obj(
            "paramName"  -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference"         -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails"           -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails"            -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader"         -> Json.obj(
          "chargeReference"       -> "XJPR5768524625",
          "portOfEntry"           -> "LHR",
          "portOfEntryName"       -> "Heathrow Airport",
          "expectedDateOfArrival" -> "2018-05-31",
          "timeOfEntry"           -> "13:20",
          "messageTypes"          -> Json.obj("messageType" -> "DeclarationCreate"),
          "travellingFrom"        -> "NON_EU Only",
          "onwardTravelGBNI"      -> "GB",
          "uccRelief"             -> false,
          "ukVATPaid"             -> false,
          "ukExcisePaid"          -> false
        ),
        "declarationTobacco"        -> Json.obj(
          "totalExciseTobacco"     -> "100.54",
          "totalCustomsTobacco"    -> "192.94",
          "totalVATTobacco"        -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity"             -> "250",
              "goodsValue"           -> "400.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "304.11",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "74.00",
              "customsGBP"           -> "79.06",
              "vatGBP"               -> "91.43"
            )
          )
        ),
        "declarationAlcohol"        -> Json.obj(
          "totalExciseAlcohol"     -> "2.00",
          "totalCustomsAlcohol"    -> "0.30",
          "totalVATAlcohol"        -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume"               -> "5",
              "goodsValue"           -> "120.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "91.23",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "2.00",
              "customsGBP"           -> "0.30",
              "vatGBP"               -> "18.70"
            )
          )
        ),
        "declarationOther"          -> Json.obj(
          "totalExciseOther"     -> "0.00",
          "totalCustomsOther"    -> "341.65",
          "totalVATOther"        -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity"             -> "1",
              "goodsValue"           -> "1500.00",
              "valueCurrency"        -> "USD",
              "valueCurrencyName"    -> "USA dollars (USD)",
              "originCountry"        -> "US",
              "originCountryName"    -> "United States of America",
              "exchangeRate"         -> "1.20",
              "exchangeRateDate"     -> "2018-10-29",
              "goodsValueGBP"        -> "1140.42",
              "VATRESClaimed"        -> false,
              "exciseGBP"            -> "0.00",
              "customsGBP"           -> "159.65",
              "vatGBP"               -> "260.01"
            )
          )
        ),
        "liabilityDetails"          -> Json.obj(
          "totalExciseGBP"  -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP"     -> "725.03",
          "grandTotalGBP"   -> "1362.46"
        ),
        "AmendmentLiabilityDetails" -> Json.obj(
          "additionalExciseGBP"  -> "102.54",
          "additionalCustomsGBP" -> "534.89",
          "additionalVATGBP"     -> "725.03",
          "additionalTotalGBP"   -> "1362.46"
        )
      )
    )
  )

  val journeyData: JsObject = Json.obj(
    "euCountryCheck"        -> "greatBritain",
    "arrivingNICheck"       -> true,
    "isUKResident"          -> false,
    "bringingOverAllowance" -> true
  )

  "an amendment submission worker" should {

    val correlationId          = "fe28db96-d9db-4220-9e12-f2d267267c29"
    val amendmentCorrelationId = "ge28db96-d9db-4220-9e12-f2d267267c30"

    "must submit paid amendments" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.SubmissionFailed,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.PendingPayment,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            None,
            sentToEtmp = true,
            None,
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            amendSentToEtmp = Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, response) = worker.tap.pull().futureValue.get
        declaration.chargeReference mustEqual ChargeReference(3)
        declaration.amendCorrelationId.get mustBe amendmentCorrelationId
        response mustEqual SubmissionResponse.Submitted
      }
    }

    "must throttle submissions" in {
      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder
        .configure(
          "workers.amendment-submission-worker.throttle.elements" -> "1",
          "workers.amendment-submission-worker.throttle.per"      -> "1 second"
        )
        .build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj())
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj())
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj())
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj())
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        worker.tap.pull().futureValue

        val startTime = LocalDateTime.now(ZoneOffset.UTC)

        worker.tap.pull().futureValue
        worker.tap.pull().futureValue
        worker.tap.pull().futureValue

        val endTime = LocalDateTime.now(ZoneOffset.UTC)

        ChronoUnit.MILLIS.between(startTime, endTime) must be > 2000L
      }
    }

    "must not process locked records" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector   = app.injector.instanceOf[HODConnector]
        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        lockRepository.lock(1)

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, _) = worker.tap.pull().futureValue.get
        declaration.chargeReference mustEqual ChargeReference(2)
      }
    }

    "must lock records when processing them" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, _) = worker.tap.pull().futureValue.get
        declaration.chargeReference mustEqual ChargeReference(1)
      }
    }

    "must not remove errored declarations from mongo" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.SubmissionFailed),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, result) = worker.tap.pull().futureValue.get
        result mustEqual SubmissionResponse.ParsingException

        repository.get(declaration.chargeReference).futureValue must be(defined)

      }
    }

    "must set the state of failed amended declarations to failed" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.SubmissionFailed),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, result) = worker.tap.pull().futureValue.get
        result mustEqual SubmissionResponse.Failed

        repository.get(declaration.chargeReference).futureValue.get.amendState.get mustBe State.SubmissionFailed

      }
    }

    "must continue to process amendments" in {
      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            Json.obj(),
            Json.obj(),
            Some(Json.obj()),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        worker.tap.pull().futureValue
        worker.tap.pull().futureValue
      }
    }

    "must only make one request to the HOD" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      server.stubFor(
        post(urlPathEqualTo("/write/audit"))
          .willReturn(aResponse().withStatus(OK))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
      await(lockRepository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(0),
            State.Paid,
            Some(State.SubmissionFailed),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(1),
            State.Paid,
            Some(State.PendingPayment),
            sentToEtmp = true,
            Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(2),
            State.Paid,
            None,
            sentToEtmp = false,
            None,
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          ),
          Declaration(
            ChargeReference(3),
            State.Paid,
            Some(State.Paid),
            sentToEtmp = true,
            amendSentToEtmp = Some(false),
            correlationId,
            Some(amendmentCorrelationId),
            journeyData,
            data,
            Some(amendData),
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val hODConnector = app.injector.instanceOf[HODConnector]

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        val worker = new AmendmentSubmissionWorker(
          repository,
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, result) = worker.tap.pull().futureValue.get

        val auditRequest = postRequestedFor(urlEqualTo("/write/audit/merged"))
        val desRequest   = postRequestedFor(urlPathEqualTo("/declarations/passengerdeclaration/v1"))

        eventually {
          server.requestsWereSent(times = 1, auditRequest) mustEqual true
          server.requestsWereSent(times = 1, desRequest) mustEqual true
        }

        result mustEqual SubmissionResponse.Submitted
        repository.get(declaration.chargeReference).futureValue must be(defined)
      }
    }
  }
}
