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

import com.github.tomakehurst.wiremock.client.WireMock.{any as _, *}
import com.typesafe.config.ConfigFactory
import connectors.HODConnector
import helpers.IntegrationSpecCommonBase
import models.declarations.{Declaration, Etmp, State}
import models.{ChargeReference, SubmissionResponse}
import org.apache.pekko.stream.Materializer
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.Eventually.eventually
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.*
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditingTools
import utils.WireMockHelper
import utils.WireMockUtils.WireMockServerImprovements

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CMADeclarationSubmissionWorkerISpec
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
      "feature.isUsingCMA" -> true,
      "workers.declaration-submission-worker.interval" -> "1 second",
      "microservice.services.des.cma.port"                 -> server.port(),
      "auditing.consumer.baseUri.port"                 -> server.port(),
      "auditing.enabled"                               -> "true"
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

  val journeyData: JsObject = Json.obj(
    "euCountryCheck"        -> "greatBritain",
    "arrivingNICheck"       -> true,
    "isUKResident"          -> false,
    "bringingOverAllowance" -> true
  )

  "a declaration submission worker" should {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "must submit paid declarations" in {
      server.stubFor(
        post(urlPathEqualTo("/declarations/simpledeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())

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
            None,
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
            None,
            journeyData,
            data,
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
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
        val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
        val hODConnector           = app.injector.instanceOf[HODConnector]

        val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        Future.sequence(services)

        val worker = new DeclarationSubmissionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, response) = worker.tap.pull().futureValue.get
        declaration.chargeReference shouldBe ChargeReference(2)
        response                    shouldBe SubmissionResponse.Submitted
      }
    }

    "must not process locked records" in {
      server.stubFor(
        post(urlPathEqualTo("/declarations/simpledeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())

      val app = builder.build()

      running(app) {

        val declarations = List(
          Declaration(
            ChargeReference(1),
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
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())
        await(lockRepository.lock(1))

        val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
        val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
        val hODConnector           = app.injector.instanceOf[HODConnector]

        val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        Future.sequence(services)

        val worker = new DeclarationSubmissionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, _) = worker.tap.pull().futureValue.get
        declaration.chargeReference shouldBe ChargeReference(2)
      }
    }

    "must not remove successfully submitted declarations from mongo" in {

      server.stubFor(
        post(urlPathEqualTo("/write/audit"))
          .willReturn(aResponse().withStatus(OK))
      )

      server.stubFor(
        post(urlPathEqualTo("/declarations/simpledeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())

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
            None,
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
            None,
            journeyData,
            data,
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
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
        val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
        val hODConnector           = app.injector.instanceOf[HODConnector]

        val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        Future.sequence(services)

        val worker = new DeclarationSubmissionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, _) = worker.tap.pull().futureValue.get

        val expectedJsonBody: String = Json
          .obj(
            "auditSource" -> "bc-passengers-declarations",
            "auditType"   -> "passengerdeclaration",
            "tags"        -> Json.obj(
              "clientIP"          -> "-",
              "path"              -> "-",
              "X-Session-ID"      -> "-",
              "Akamai-Reputation" -> "-",
              "X-Request-ID"      -> "-",
              "deviceID"          -> "-",
              "clientPort"        -> "-",
              "transactionName"   -> "passengerdeclarationsubmitted"
            ),
            "detail"      -> Json.toJsObject(data.as[Etmp])
          )
          .toString()
          .stripMargin

        val request =
          postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(equalToJson(expectedJsonBody, true, true))

        eventually {
          server.requestsWereSent(times = 1, request) shouldBe true
        }

        repository.asInstanceOf[DeclarationsRepository].get(declaration.chargeReference).futureValue should be(defined)
      }
    }

    "must not remove errored declarations from mongo" in {
      server.stubFor(
        post(urlPathEqualTo("/declarations/simpledeclaration/v1"))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())

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
            None,
            Json.obj(),
            Json.obj(),
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
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
        val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
        val hODConnector           = app.injector.instanceOf[HODConnector]

        val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        Future.sequence(services)

        val worker = new DeclarationSubmissionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, result) = worker.tap.pull().futureValue.get
        result shouldBe SubmissionResponse.ParsingException

        repository.asInstanceOf[DeclarationsRepository].get(declaration.chargeReference).futureValue should be(defined)
      }
    }

    "must set the state of failed declarations to failed" in {
      server.stubFor(
        post(urlPathEqualTo("/declarations/simpledeclaration/v1"))
          .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())

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
            None,
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
            None,
            journeyData,
            data,
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
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
        val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
        val hODConnector           = app.injector.instanceOf[HODConnector]

        val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        Future.sequence(services)

        val worker = new DeclarationSubmissionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, result) = worker.tap.pull().futureValue.get
        
        result shouldBe SubmissionResponse.Failed

        repository
          .asInstanceOf[DeclarationsRepository]
          .get(declaration.chargeReference)
          .futureValue
          .get
          .state shouldBe State.SubmissionFailed
      }
    }

    "must only make one request to the HOD" in {

      server.stubFor(
        post(urlPathEqualTo("/declarations/simpledeclaration/v1"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      server.stubFor(
        post(urlPathEqualTo("/write/audit"))
          .willReturn(aResponse().withStatus(OK))
      )

      await(repository.collection.deleteMany(Filters.empty()).toFuture())
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
            None,
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
            None,
            journeyData,
            data,
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
            journeyData,
            data,
            None,
            LocalDateTime.now(ZoneOffset.UTC)
          )
        )

        await(repository.collection.insertMany(declarations).toFuture())

        val declarationsRepository = app.injector.instanceOf[DeclarationsRepository]
        val chargeReferenceService = app.injector.instanceOf[ChargeReferenceService]
        val hODConnector           = app.injector.instanceOf[HODConnector]

        val services = Seq(declarationsRepository.started, chargeReferenceService.started, lockRepository.started)

        val auditConnector = app.injector.instanceOf[AuditConnector]
        val auditingTools  = app.injector.instanceOf[AuditingTools]

        Future.sequence(services)

        val worker = new DeclarationSubmissionWorker(
          repository.asInstanceOf[DeclarationsRepository],
          lockRepository,
          hODConnector,
          Configuration(ConfigFactory.load(System.getProperty("config.resource"))),
          auditConnector,
          auditingTools
        )

        val (declaration, result) = worker.tap.pull().futureValue.get

        val auditRequest = postRequestedFor(urlEqualTo("/write/audit/merged"))
        val desRequest   = postRequestedFor(urlPathEqualTo("/declarations/simpledeclaration/v1"))

        eventually {
          server.requestsWereSent(times = 1, auditRequest) shouldBe true
          server.requestsWereSent(times = 1, desRequest)   shouldBe true
        }

        result shouldBe SubmissionResponse.Submitted

        repository.asInstanceOf[DeclarationsRepository].get(declaration.chargeReference).futureValue should be(defined)
      }
    }
  }
}
