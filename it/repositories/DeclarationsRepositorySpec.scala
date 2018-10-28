package repositories

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
  with ScalaFutures with IntegrationPatience with OptionValues {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a declarations repository" - {

    "must insert and remove declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        repository.started.futureValue
        val id       = repository.insert(Json.obj()).futureValue
        val document = repository.get(id).futureValue.value

        document mustEqual Json.obj(
          "_id"  -> id.value.toString,
          "data" -> Json.obj(
            "simpleDeclarationRequest" -> Json.obj(
              "requestDetail" -> Json.obj(
                "declarationHeader" -> Json.obj(
                  "chargeReference" -> id.value.toString
                )
              )
            )
          )
        )

        repository.remove(id).futureValue
        repository.get(id).futureValue mustNot be (defined)
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
