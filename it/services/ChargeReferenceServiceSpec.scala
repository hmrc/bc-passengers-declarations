package services

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global

class ChargeReferenceServiceSpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience {

  private lazy val builder = new GuiceApplicationBuilder()

  "a charge reference service" - {

    "must return sequential ids" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val service = app.injector.instanceOf[ChargeReferenceService]

        service.started.futureValue
        val first  = service.nextChargeReference().futureValue
        val second = service.nextChargeReference().futureValue

        (second.value.toInt - first.value.toInt) mustEqual 1
      }
    }

    "must not fail if the collection already has a document on startup" in {

      database.flatMap {
        db =>
          db.drop().flatMap {
            _ =>
              db.collection[JSONCollection]("charge-reference")
                .insert(Json.obj(
                  "_id"             -> "counter",
                  "chargeReference" -> 10
                ))
          }
      }.futureValue

      val app = builder.build()

      running(app) {

        val service = app.injector.instanceOf[ChargeReferenceService]
        service.started.futureValue
      }
    }
  }
}
