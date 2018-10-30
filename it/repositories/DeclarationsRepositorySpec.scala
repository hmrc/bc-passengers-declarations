package repositories

import models.Declaration
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import reactivemongo.api.indexes.IndexType
import reactivemongo.play.json.collection.JSONCollection
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

class DeclarationsRepositorySpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with Inside {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a declarations repository" - {

    "must insert and remove declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        repository.started.futureValue
        val chargeReference = repository.insert(Json.obj()).futureValue
        val document        = repository.get(chargeReference).futureValue.value

        inside(document) {
          case Declaration(id, data, _) =>

            id mustEqual chargeReference
            data mustEqual Json.obj(
              "simpleDeclarationRequest" -> Json.obj(
                "requestDetail" -> Json.obj(
                  "declarationHeader" -> Json.obj(
                    "chargeReference" -> chargeReference.value.toString
                  )
                )
              )
            )
        }

        repository.remove(chargeReference).futureValue
        repository.get(chargeReference).futureValue mustNot be (defined)
      }
    }

    "must ensure indices" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        repository.started.futureValue

        val indices = database.flatMap {
          _.collection[JSONCollection]("declarations")
            .indexesManager.list()
        }.futureValue

        indices.find {
          index =>
            index.name.contains("declarations-index") &&
            index.key == Seq("lastUpdated" -> IndexType.Ascending)
        } mustBe defined
      }
    }
  }
}
