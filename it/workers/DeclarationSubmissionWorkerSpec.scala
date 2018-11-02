package workers

import java.time.LocalDateTime

import models.{ChargeReference, SubmissionResponse}
import models.declarations.{Declaration, State}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.mockito.Mockito._
import org.mockito.Matchers._
import com.github.tomakehurst.wiremock.client.WireMock.{any => _, _}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection
import repositories.{DeclarationsRepository, LockRepository}
import suite.MongoSuite
import utils.WireMockHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class DeclarationSubmissionWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar with WireMockHelper {

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.declaration-submission-worker.interval" -> "1 second",
      "microservice.services.des.port" -> server.port()
    )

  "a declaration submission worker" - {

    "must submit paid declarations" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Failed, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, Json.obj(), LocalDateTime.now)
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

    "must not process locked records" in {

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(1), State.Paid, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, Json.obj(), LocalDateTime.now)
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
          Declaration(ChargeReference(1), State.Paid, Json.obj(), LocalDateTime.now)
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

      server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
        .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Failed, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        val repository = app.injector.instanceOf[DeclarationsRepository]
        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val (declaration, _) = worker.tap.pull.futureValue.value
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
          Declaration(ChargeReference(0), State.Failed, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, Json.obj(), LocalDateTime.now)
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

        repository.get(declaration.chargeReference).futureValue must be (defined)
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
          Declaration(ChargeReference(0), State.Failed, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.PendingPayment, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, Json.obj(), LocalDateTime.now)
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

        repository.get(declaration.chargeReference).futureValue.value.state mustEqual State.Failed
      }
    }

    "must continue to process declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, Json.obj(), LocalDateTime.now)
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
        Declaration(ChargeReference(0), State.Paid, Json.obj(), LocalDateTime.now),
        Declaration(ChargeReference(1), State.Paid, Json.obj(), LocalDateTime.now)
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

      val declarations = List(
        Declaration(ChargeReference(0), State.Paid, Json.obj(), LocalDateTime.now),
        Declaration(ChargeReference(1), State.Paid, Json.obj(), LocalDateTime.now)
      )

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert[Declaration](ordered = true)
          .many(declarations)
      }.futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[DeclarationSubmissionWorker]

        worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(0)

        val mongo = app.injector.instanceOf[ReactiveMongoApi]

        mongo.driver.close(3 seconds)

        worker.tap.pull.futureValue.value._1.chargeReference mustEqual ChargeReference(1)
      }
    }
  }
}