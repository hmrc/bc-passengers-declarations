package workers

import java.time.{LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit
import com.github.tomakehurst.wiremock.client.WireMock.{any => _, _}
import models.declarations.{Declaration, Etmp, State}
import models.{ChargeReference, SubmissionResponse}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.netcrusher.core.reactor.NioReactor
import org.netcrusher.tcp.TcpCrusherBuilder
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import reactivemongo.play.json.collection.JSONCollection
import repositories.{DeclarationsRepository, LockRepository}
import suite.MongoSuite
import utils.WireMockHelper
import utils.WireMockUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

class AmendmentSubmissionWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar with WireMockHelper with Eventually {

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.amendment-submission-worker.interval" -> "1 second",
      "microservice.services.des.port" -> server.port(),
      "auditing.consumer.baseUri.port" -> server.port(),
      "auditing.enabled" -> "true"
    )

  val data: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference" -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails" -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails" -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader" -> Json.obj("chargeReference" -> "XJPR5768524625", "portOfEntry" -> "LHR", "portOfEntryName" -> "Heathrow Airport", "expectedDateOfArrival" -> "2018-05-31", "timeOfEntry" -> "13:20", "messageTypes" -> Json.obj("messageType" -> "DeclarationCreate"), "travellingFrom" -> "NON_EU Only", "onwardTravelGBNI" -> "GB", "uccRelief" -> false, "ukVATPaid" -> false, "ukExcisePaid" -> false),
        "declarationTobacco" -> Json.obj(
          "totalExciseTobacco" -> "100.54",
          "totalCustomsTobacco" -> "192.94",
          "totalVATTobacco" -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity" -> "250",
              "goodsValue" -> "400.00",
              "valueCurrency" -> "USD",
              "valueCurrencyName" -> "USA dollars (USD)",
              "originCountry" -> "US",
              "originCountryName" -> "United States of America",
              "exchangeRate" -> "1.20",
              "exchangeRateDate" -> "2018-10-29",
              "goodsValueGBP" -> "304.11",
              "VATRESClaimed" -> false,
              "exciseGBP" -> "74.00",
              "customsGBP" -> "79.06",
              "vatGBP" -> "91.43"
            )
          )
        ),
        "declarationAlcohol" -> Json.obj(
          "totalExciseAlcohol" -> "2.00",
          "totalCustomsAlcohol" -> "0.30",
          "totalVATAlcohol" -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume" -> "5",
              "goodsValue" -> "120.00",
              "valueCurrency" -> "USD",
              "valueCurrencyName" -> "USA dollars (USD)",
              "originCountry" -> "US",
              "originCountryName" -> "United States of America",
              "exchangeRate" -> "1.20",
              "exchangeRateDate" -> "2018-10-29",
              "goodsValueGBP" -> "91.23",
              "VATRESClaimed" -> false,
              "exciseGBP" -> "2.00",
              "customsGBP" -> "0.30",
              "vatGBP" -> "18.70"
            )
          )
        ),
        "declarationOther" -> Json.obj(
          "totalExciseOther" -> "0.00",
          "totalCustomsOther" -> "341.65",
          "totalVATOther" -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity" -> "1",
              "goodsValue" -> "1500.00",
              "valueCurrency" -> "USD",
              "valueCurrencyName" -> "USA dollars (USD)",
              "originCountry" -> "US",
              "originCountryName" -> "United States of America",
              "exchangeRate" -> "1.20",
              "exchangeRateDate" -> "2018-10-29",
              "goodsValueGBP" -> "1140.42",
              "VATRESClaimed" -> false,
              "exciseGBP" -> "0.00",
              "customsGBP" -> "159.65",
              "vatGBP" -> "260.01"
            )
          )
        ),
        "liabilityDetails" -> Json.obj(
          "totalExciseGBP" -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP" -> "725.03",
          "grandTotalGBP" -> "1362.46"
        )
      )
    )
  )

  val amendData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference" -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails" -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails" -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader" -> Json.obj("chargeReference" -> "XJPR5768524625", "portOfEntry" -> "LHR", "portOfEntryName" -> "Heathrow Airport", "expectedDateOfArrival" -> "2018-05-31", "timeOfEntry" -> "13:20", "messageTypes" -> Json.obj("messageType" -> "DeclarationCreate"), "travellingFrom" -> "NON_EU Only", "onwardTravelGBNI" -> "GB", "uccRelief" -> false, "ukVATPaid" -> false, "ukExcisePaid" -> false),
        "declarationTobacco" -> Json.obj(
          "totalExciseTobacco" -> "100.54",
          "totalCustomsTobacco" -> "192.94",
          "totalVATTobacco" -> "149.92",
          "declarationItemTobacco" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cigarettes",
              "quantity" -> "250",
              "goodsValue" -> "400.00",
              "valueCurrency" -> "USD",
              "valueCurrencyName" -> "USA dollars (USD)",
              "originCountry" -> "US",
              "originCountryName" -> "United States of America",
              "exchangeRate" -> "1.20",
              "exchangeRateDate" -> "2018-10-29",
              "goodsValueGBP" -> "304.11",
              "VATRESClaimed" -> false,
              "exciseGBP" -> "74.00",
              "customsGBP" -> "79.06",
              "vatGBP" -> "91.43"
            )
          )
        ),
        "declarationAlcohol" -> Json.obj(
          "totalExciseAlcohol" -> "2.00",
          "totalCustomsAlcohol" -> "0.30",
          "totalVATAlcohol" -> "18.70",
          "declarationItemAlcohol" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Cider",
              "volume" -> "5",
              "goodsValue" -> "120.00",
              "valueCurrency" -> "USD",
              "valueCurrencyName" -> "USA dollars (USD)",
              "originCountry" -> "US",
              "originCountryName" -> "United States of America",
              "exchangeRate" -> "1.20",
              "exchangeRateDate" -> "2018-10-29",
              "goodsValueGBP" -> "91.23",
              "VATRESClaimed" -> false,
              "exciseGBP" -> "2.00",
              "customsGBP" -> "0.30",
              "vatGBP" -> "18.70"
            )
          )
        ),
        "declarationOther" -> Json.obj(
          "totalExciseOther" -> "0.00",
          "totalCustomsOther" -> "341.65",
          "totalVATOther" -> "556.41",
          "declarationItemOther" -> Json.arr(
            Json.obj(
              "commodityDescription" -> "Television",
              "quantity" -> "1",
              "goodsValue" -> "1500.00",
              "valueCurrency" -> "USD",
              "valueCurrencyName" -> "USA dollars (USD)",
              "originCountry" -> "US",
              "originCountryName" -> "United States of America",
              "exchangeRate" -> "1.20",
              "exchangeRateDate" -> "2018-10-29",
              "goodsValueGBP" -> "1140.42",
              "VATRESClaimed" -> false,
              "exciseGBP" -> "0.00",
              "customsGBP" -> "159.65",
              "vatGBP" -> "260.01"
            )
          )
        ),
        "liabilityDetails" -> Json.obj(
          "totalExciseGBP" -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP" -> "725.03",
          "grandTotalGBP" -> "1362.46"
        ),
        "AmendmentLiabilityDetails" -> Json.obj(
        "additionalExciseGBP" -> "102.54",
        "additionalCustomsGBP" -> "534.89",
        "additionalVATGBP" -> "725.03",
        "additionalTotalGBP" -> "1362.46"
        )
      )
    )
  )

  val journeyData: JsObject = Json.obj(
    "euCountryCheck" -> "greatBritain",
    "arrivingNICheck" -> true,
    "isUKResident" -> false,
    "bringingOverAllowance" -> true)

  "an amendment submission worker" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
    val amendmentCorrelationId = "ge28db96-d9db-4220-9e12-f2d267267c30"

    "must submit paid amendments" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, None,sentToEtmp = false, None, correlationId, Some(amendmentCorrelationId), journeyData, data, None,  LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(1), State.PendingPayment, None, sentToEtmp = false, None,correlationId, Some(amendmentCorrelationId), journeyData, data, None, LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(2), State.Paid, None,sentToEtmp = true, None, correlationId, Some(amendmentCorrelationId), journeyData, data, None, LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(3), State.Paid, Some(State.Paid),sentToEtmp = true, amendSentToEtmp = Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val (declaration, response) = worker.tap.pull.futureValue.value
        declaration.chargeReference mustEqual ChargeReference(3)
        declaration.amendCorrelationId.get mustBe amendmentCorrelationId
        response mustEqual SubmissionResponse.Submitted
      }
    }

    "must throttle submissions" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.configure(
        "workers.amendment-submission-worker.throttle.elements" -> "1",
        "workers.amendment-submission-worker.throttle.per" -> "1 second"
      ).build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
          Declaration(ChargeReference(1), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
          Declaration(ChargeReference(2), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj())),
          Declaration(ChargeReference(3), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        worker.tap.pull.futureValue

        val startTime = LocalDateTime.now(ZoneOffset.UTC)

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        val endTime = LocalDateTime.now(ZoneOffset.UTC)

        ChronoUnit.MILLIS.between(startTime, endTime) must be > 2000L
      }
    }

    "must not process locked records" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(1), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(2), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.lock(1)

        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val (declaration, _) = worker.tap.pull.futureValue.value
        declaration.chargeReference mustEqual ChargeReference(2)
      }
    }

    "must lock records when processing them" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(1), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]
        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val (declaration, _) = worker.tap.pull.futureValue.value
        lockRepository.isLocked(1)
        declaration.chargeReference mustEqual ChargeReference(1)
      }
    }

    "must not remove successfully submitted amendments from mongo" in {

      server.stubFor(post(urlPathEqualTo("/write/audit"))
        .willReturn(aResponse().withStatus(OK)
      ))

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(2), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false), correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val repository = app.injector.instanceOf[DeclarationsRepository]

        val (declaration, _) = worker.tap.pull.futureValue.value

        val expectedJsonBody: String = Json.obj(
          "auditSource" -> "bc-passengers-declarations",
          "auditType" -> "passengerdeclaration",
          "tags" -> Json.obj(
            "clientIP" -> "-",
            "path" -> "-",
            "X-Session-ID" -> "-",
            "Akamai-Reputation" -> "-",
            "X-Request-ID" -> "-",
            "deviceID" -> "-",
            "clientPort" -> "-",
            "transactionName" -> "passengerdeclarationsubmitted"
          ),
          "detail" -> Json.toJsObject(amendData.as[Etmp])).toString().stripMargin

        val request = postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(equalToJson(expectedJsonBody, true, true))

        eventually {
          server.requestsWereSent(times = 1, request) mustEqual true
        }

        repository.get(declaration.chargeReference).futureValue must be(defined)
      }
    }

    "must not remove errored declarations from mongo" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(2), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val (declaration, result) = worker.tap.pull.futureValue.value
        result mustEqual SubmissionResponse.ParsingException

        repository.get(declaration.chargeReference).futureValue must be(defined)
      }
    }

    "must set the state of failed amended declarations to failed" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed), sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PendingPayment), sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(2), State.Paid, Some(State.Paid), sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val (declaration, result) = worker.tap.pull.futureValue.value
        result mustEqual SubmissionResponse.Failed

        repository.get(declaration.chargeReference).futureValue.value.amendState.get mustBe State.SubmissionFailed
      }
    }

    "must continue to process amendments" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(1), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
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
        Declaration(ChargeReference(0), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
        Declaration(ChargeReference(1), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
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

      val app = builder.overrides(
        bind[LockRepository].toInstance(mockLockRepository)
      ).build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

        worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(1)
        worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(0)
      }
    }

    "must continue processing after a transient failure getting paid declarations" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )
      database.flatMap(_.drop()).futureValue

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert(ordered = true)
          .one(Declaration(ChargeReference(0), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data,Some(amendData), LocalDateTime.now(ZoneOffset.UTC)))
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

          val worker = app.injector.instanceOf[AmendmentSubmissionWorker]

          worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(0)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert(ordered = true)
              .one(Declaration(ChargeReference(1), State.Paid, Some(State.Paid),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data,Some(amendData), LocalDateTime.now(ZoneOffset.UTC)))
          }.futureValue

          proxy.open()

          worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(1)
        }
      } finally {

        proxy.close()
        reactor.close()
      }
    }


    "must only make one request to the HOD" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      server.stubFor(post(urlPathEqualTo("/write/audit"))
        .willReturn(aResponse().withStatus(OK)
      ))


      database.flatMap(_.drop()).futureValue
      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.SubmissionFailed),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(1), State.Paid, Some(State.PendingPayment),sentToEtmp = true, Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(2), State.Paid, None,sentToEtmp = false, None,correlationId,  Some(amendmentCorrelationId), journeyData, data, None, LocalDateTime.now(ZoneOffset.UTC)),
          Declaration(ChargeReference(3), State.Paid, Some(State.Paid),sentToEtmp = true, amendSentToEtmp = Some(false),correlationId, Some(amendmentCorrelationId), journeyData, data, Some(amendData), LocalDateTime.now(ZoneOffset.UTC))
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[AmendmentSubmissionWorker]
        val (declaration, result) = worker.tap.pull.futureValue.value

        val auditRequest = postRequestedFor(urlEqualTo("/write/audit/merged"))
        val desRequest = postRequestedFor(urlPathEqualTo("/declarations/passengerdeclaration/v1"))

        eventually{
          server.requestsWereSent(times = 1, auditRequest) mustEqual true
          server.requestsWereSent(times = 1, desRequest) mustEqual true
        }

        result mustEqual SubmissionResponse.Submitted
        repository.get(declaration.chargeReference).futureValue must be(defined)
      }
    }
  }
}
