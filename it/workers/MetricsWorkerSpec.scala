package workers

import java.time.LocalDateTime

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import logger.TestLoggerAppender
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsPath, Json, Reads}
import play.api.test.FakeRequest
import play.api.test.Helpers.running
import reactivemongo.play.json.collection.JSONCollection
import suite.MongoSuite
import utils.WireMockHelper
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.test.Helpers._

class MetricsWorkerSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar with WireMockHelper {

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "workers.metrics-worker.interval" -> "1 second"
    )

  val declarationsStatusReads: Reads[DeclarationsStatus] = (
    (JsPath \ "counters" \ "pending-payment-counter" \ "count").read[Int] and
    (JsPath \ "counters" \ "payment-complete-counter" \ "count").read[Int] and
    (JsPath \ "counters" \"payment-failed-counter" \ "count").read[Int] and
    (JsPath \ "counters" \ "payment-cancelled-counter" \ "count").read[Int] and
    (JsPath \ "counters" \ "failed-submission-counter" \ "count").read[Int]
  )(DeclarationsStatus.apply _)

  "A metrics worker" - {

    "must update metrics to match the current collection state" in {

      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

      database.flatMap(_.drop()).futureValue

      TestLoggerAppender.queue.dequeueAll(_ => true)

      database.flatMap {
        _.collection[JSONCollection]("declarations")
          .insert[Declaration](ordered = true)
          .many(
            List(
              Declaration(ChargeReference(0), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
              Declaration(ChargeReference(1), State.PaymentFailed, correlationId, Json.obj(), LocalDateTime.now)
            )
          )
      }.futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val worker = app.injector.instanceOf[MetricsWorker]

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        worker.tap.pull.futureValue mustBe Some(DeclarationsStatus(1, 0, 1, 0, 0))

        val r = route(app, FakeRequest("GET", "/admin/metrics")).get
        r.futureValue

        Json.fromJson[DeclarationsStatus](contentAsJson(r))(declarationsStatusReads).get mustBe DeclarationsStatus(1, 0, 1, 0, 0)

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(
              List(
                Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj(), LocalDateTime.now),
                Declaration(ChargeReference(3), State.PaymentCancelled, correlationId, Json.obj(), LocalDateTime.now),
                Declaration(ChargeReference(4), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now)
              )
            )
        }.futureValue

        worker.tap.pull.futureValue mustBe Some(DeclarationsStatus(1, 1, 1, 1, 1))


        val r1 = route(app, FakeRequest("GET", "/admin/metrics")).get
        r1.futureValue
        Json.fromJson[DeclarationsStatus](contentAsJson(r1))(declarationsStatusReads).get mustBe DeclarationsStatus(1, 1, 1, 1, 1)
      }
    }
  }
}