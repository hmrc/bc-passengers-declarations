package repositories

import java.time.LocalDateTime

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import models.{ChargeReference, DeclarationsStatus}
import models.declarations.{Declaration, State}
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import reactivemongo.api.indexes.IndexType
import reactivemongo.play.json.collection.JSONCollection
import suite.FailOnUnindexedQueries

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

class DeclarationsRepositorySpec extends FreeSpec with MustMatchers with FailOnUnindexedQueries
  with ScalaFutures with IntegrationPatience with OptionValues with Inside with EitherValues {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a declarations repository" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "must insert and remove declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val document = repository.insert(Json.obj("simpleDeclarationRequest" -> Json.obj("foo" -> "bar")), correlationId).futureValue.right.value

        inside(document) {
          case Declaration(id, _, cid, data, _) =>

            id mustEqual document.chargeReference
            cid mustEqual correlationId
            data mustEqual Json.obj(
              "simpleDeclarationRequest" -> Json.obj(
                "foo" -> "bar",
                "requestCommon" -> Json.obj(
                  "acknowledgementReference" -> (document.chargeReference.toString+"0")
                ),
                "requestDetail" -> Json.obj(
                  "declarationHeader" -> Json.obj(
                    "chargeReference" -> document.chargeReference.toString
                  )
                )
              )
            )
        }

        repository.remove(document.chargeReference).futureValue
        repository.get(document.chargeReference).futureValue mustNot be(defined)
      }
    }

    "must ensure indices" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val indices = database.flatMap {
          _.collection[JSONCollection]("declarations")
            .indexesManager.list()
        }.futureValue

        indices.find {
          index =>
            index.name.contains("declarations-last-updated-index") &&
              index.key == Seq("lastUpdated" -> IndexType.Ascending)
        } mustBe defined


        indices.find {
          index =>
            index.name.contains("declarations-state-index") &&
              index.key == Seq("state" -> IndexType.Ascending)
        } mustBe defined
      }
    }

    "must provide a stream of stale declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.configure("declarations.payment-no-response-timeout" -> "1 minute").build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.PaymentFailed, correlationId, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.PaymentCancelled, correlationId, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(3), State.Paid, correlationId, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(4), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(5), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(6), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val staleDeclarations = repository.staleDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        staleDeclarations.size mustEqual 3
        staleDeclarations.map(_.chargeReference) must contain allOf (ChargeReference(0), ChargeReference(1), ChargeReference(2))
      }
    }

    "must set the state of a declaration" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declaration = repository.insert(Json.obj("simpleDeclarationRequest" -> Json.obj("foo" -> "bar")), correlationId).futureValue.right.value

        val updatedDeclaration = repository.setState(declaration.chargeReference, State.Paid).futureValue

        updatedDeclaration.state mustEqual State.Paid
      }
    }

    "must provide a stream of paid declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(2), State.SubmissionFailed, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(3), State.Paid, correlationId, Json.obj(), LocalDateTime.now),
          Declaration(ChargeReference(4), State.Paid, correlationId, Json.obj(), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val paidDeclarations = repository.paidDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        paidDeclarations.map(_.chargeReference) must contain only (
          ChargeReference(1), ChargeReference(3), ChargeReference(4)
        )
      }
    }

    "must provide a stream of submission-failed declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj()),
          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj()),
          Declaration(ChargeReference(2), State.SubmissionFailed, correlationId, Json.obj()),
          Declaration(ChargeReference(3), State.PendingPayment, correlationId, Json.obj())
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val failedDeclarations = repository.failedDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        failedDeclarations.size mustEqual 2
        failedDeclarations.map(_.chargeReference) must contain only (ChargeReference(0), ChargeReference(2))
      }
    }

    "must fail to insert invalid declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val errors = repository.insert(Json.obj(), correlationId).futureValue.left.value

        errors must contain ("""object has missing required properties (["foo"])""")
      }
    }

    "reads the correct number of declaration states" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, correlationId, Json.obj()),

          Declaration(ChargeReference(1), State.Paid, correlationId, Json.obj()),
          Declaration(ChargeReference(2), State.Paid, correlationId, Json.obj()),

          Declaration(ChargeReference(3), State.PendingPayment, correlationId, Json.obj()),
          Declaration(ChargeReference(4), State.PendingPayment, correlationId, Json.obj()),
          Declaration(ChargeReference(5), State.PendingPayment, correlationId, Json.obj()),

          Declaration(ChargeReference(6), State.PaymentCancelled, correlationId, Json.obj()),
          Declaration(ChargeReference(7), State.PaymentCancelled, correlationId, Json.obj()),
          Declaration(ChargeReference(8), State.PaymentCancelled, correlationId, Json.obj()),
          Declaration(ChargeReference(9), State.PaymentCancelled, correlationId, Json.obj()),

          Declaration(ChargeReference(10), State.PaymentFailed, correlationId, Json.obj()),
          Declaration(ChargeReference(11), State.PaymentFailed, correlationId, Json.obj()),
          Declaration(ChargeReference(12), State.PaymentFailed, correlationId, Json.obj()),
          Declaration(ChargeReference(13), State.PaymentFailed, correlationId, Json.obj()),
          Declaration(ChargeReference(14), State.PaymentFailed, correlationId, Json.obj())

        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert[Declaration](ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        repository.metricsCount.futureValue mustBe DeclarationsStatus(
          pendingPaymentCount = 3,
          paymentCompleteCount = 2,
          paymentFailedCount = 5,
          paymentCancelledCount = 4,
          failedSubmissionCount = 1
        )

      }
    }
  }
}
