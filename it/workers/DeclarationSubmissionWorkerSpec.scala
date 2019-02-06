package workers

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.{any => _, _}
import models.declarations.{Declaration, State}
import models.{ChargeReference, SubmissionResponse}
import org.apache.bcel.verifier.exc.VerificationException
import org.mockito.Matchers._
import org.mockito.Mockito.{verify => mVerify, _}
import org.netcrusher.core.reactor.NioReactor
import org.netcrusher.tcp.TcpCrusherBuilder
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import reactivemongo.play.json.collection.JSONCollection
import repositories.{DeclarationsRepository, LockRepository}
import suite.MongoSuite
import util.DeclarationDataTransformers
import utils.WireMockHelper
import utils.WireMockUtils._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

class DeclarationSubmissionWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar with WireMockHelper with Eventually {

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.declaration-submission-worker.interval" -> "1 second",
      "microservice.services.des.port" -> server.port(),
      "auditing.consumer.baseUri.port" -> server.port(),
      "auditing.enabled" -> "true"
    )

  "a declaration submission worker" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "must submit paid declarations" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, response) = worker.tap.pull.futureValue.value
        declaration.chargeReference mustEqual ChargeReference(2)
        response mustEqual SubmissionResponse.Submitted
      }
    }

    "must throttle submissions" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.configure(
        "workers.declaration-submission-worker.throttle.elements" -> "1",
        "workers.declaration-submission-worker.throttle.per" -> "1 second"
      ).build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, correlationId, Json.obj()),
          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj()),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj()),
          Declaration(ChargeReference(3), State.Paid, correlationId, Json.obj())
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        worker.tap.pull.futureValue

        val startTime = LocalDateTime.now

        worker.tap.pull.futureValue
        worker.tap.pull.futureValue
        worker.tap.pull.futureValue

        val endTime = LocalDateTime.now

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
          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]

        lockRepository.lock(1)

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, response) = worker.tap.pull.futureValue.value
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
          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val lockRepository = app.injector.instanceOf[LockRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, _) = worker.tap.pull.futureValue.value
        lockRepository.isLocked(1)
        declaration.chargeReference mustEqual ChargeReference(1)
      }
    }

    "must remove successfully submitted declarations from mongo" in {

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
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj("data" -> 1), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj("data" -> 2), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj("data" -> 3), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val repository = app.injector.instanceOf[DeclarationsRepository]

        val (declaration, _) = worker.tap.pull.futureValue.value

        val expectedJsonBody =
          """
            |{
            |    "auditSource": "bc-passengers-declarations",
            |    "auditType": "passengerdeclaration",
            |    "tags": {
            |        "clientIP": "-",
            |        "path": "-",
            |        "X-Session-ID": "-",
            |        "Akamai-Reputation": "-",
            |        "X-Request-ID": "-",
            |        "deviceID": "-",
            |        "clientPort": "-",
            |        "transactionName": "passengerdeclarationsubmitted"
            |    },
            |    "detail": {
            |        "data": 3
            |    }
            |}
          """.stripMargin

        val request = postRequestedFor(urlEqualTo("/write/audit")).withRequestBody(equalToJson(expectedJsonBody, true, true))

        eventually {
          server.requestsWereSent(times = 1, request) mustEqual true
        }

        repository.get(declaration.chargeReference).futureValue mustNot be(defined)
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
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, result) = worker.tap.pull.futureValue.value
        result mustEqual SubmissionResponse.Error

        repository.get(declaration.chargeReference).futureValue must be(defined)
      }
    }

    "must set the state of failed declarations to failed" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, result) = worker.tap.pull.futureValue.value
        result mustEqual SubmissionResponse.Failed

        repository.get(declaration.chargeReference).futureValue.value.state mustEqual State.SubmissionFailed
      }
    }

    "must continue to process declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
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
        Declaration(ChargeReference(0), State.Paid, correlationId, Json.obj(), LocalDateTime.now),
        Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
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

      val app = builder.overrides(
        bind[LockRepository].toInstance(mockLockRepository)
      ).build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(1)
        worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(0)
      }
    }

    "must continue processing after a transient failure getting paid declarations" in {

      database.flatMap(_.drop()).futureValue

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert[Declaration](ordered = true)
          .one(Declaration(ChargeReference(0), State.Paid, correlationId, Json.obj()))
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

          val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

          worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(0)

          proxy.close()

          database.flatMap {
            _.collection[JSONCollection]("declarations")
              .insert[Declaration](ordered = true)
              .one(Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj()))
          }.futureValue

          proxy.open()

          worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(1)
        }
      } finally {

        proxy.close()
        reactor.close()
      }
    }


    "must only make one request if declaration data transformed from v1.1.5 to v1.1.0 is submitted successfully" in {

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
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]
        val (declaration, result) = worker.tap.pull.futureValue.value

        val auditRequest = postRequestedFor(urlEqualTo("/write/audit/merged"))
        val desRequest = postRequestedFor(urlPathEqualTo("/declarations/passengerdeclaration/v1"))

        eventually{
          server.requestsWereSent(times = 1, auditRequest) mustEqual true
          server.requestsWereSent(times = 1, desRequest) mustEqual true
        }

        result mustEqual SubmissionResponse.Submitted

        repository.get(declaration.chargeReference).futureValue mustNot be(defined)
      }
    }

    "must resubmit the declaration in v1.1.5 if the v1.1.0 format results in a BAD_REQUEST" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1")).inScenario("Failure then Success")
        .willReturn(aResponse().withStatus(BAD_REQUEST))
        .willSetStateTo("Initial declaration failed")
      )

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1")).inScenario("Failure then Success")
        .whenScenarioStateIs("Initial declaration failed")
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      server.stubFor(post(urlPathEqualTo("/write/audit")).inScenario("Failure then Success")
        .whenScenarioStateIs("Initial declaration failed")
        .willReturn(aResponse().withStatus(OK)
        ))


      database.flatMap(_.drop()).futureValue
      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, result) = worker.tap.pull.futureValue.value

        val auditRequest = postRequestedFor(urlEqualTo("/write/audit"))
        val desRequest = postRequestedFor(urlPathEqualTo("/declarations/passengerdeclaration/v1"))

        eventually{
          server.requestsWereSent(times = 1, auditRequest) mustEqual true
          server.requestsWereSent(times = 2, desRequest) mustEqual true
        }

        result mustEqual SubmissionResponse.Submitted

        repository.get(declaration.chargeReference).futureValue mustNot be(defined)
      }
    }

    "must set the declaration state to failed if submitting the declaration in v1.1.0 format and also v1.1.5 format resulted in Failures" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1")).inScenario("Failure then Failure")
        .willReturn(aResponse().withStatus(BAD_REQUEST))
        .willSetStateTo("Initial declaration failed")
      )

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1")).inScenario("Failure then Failure")
        .whenScenarioStateIs("Initial declaration failed")
        .willReturn(aResponse().withStatus(BAD_REQUEST))
      )

      server.stubFor(post(urlPathEqualTo("/write/audit")).inScenario("Failure then Failure")
        .whenScenarioStateIs("Initial declaration failed")
        .willReturn(aResponse().withStatus(OK)
        ))

      database.flatMap(_.drop()).futureValue
      val app = builder.build()


      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, result) = worker.tap.pull.futureValue.value

        val auditRequest = postRequestedFor(urlEqualTo("/write/audit"))
        val desRequest = postRequestedFor(urlPathEqualTo("/declarations/passengerdeclaration/v1"))

        eventually {
          server.requestsWereSent(times = 0, auditRequest) mustEqual true
          server.requestsWereSent(times = 2, desRequest) mustEqual true
        }

        result mustEqual SubmissionResponse.Failed

        repository.get(declaration.chargeReference).futureValue must be(defined)
      }
    }
  }
}
