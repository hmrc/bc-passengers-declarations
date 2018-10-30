package repositories

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.DefaultDB
import reactivemongo.core.actors.Exceptions.PrimaryUnavailableException
import reactivemongo.core.errors.{ConnectionException, DatabaseException}
import reactivemongo.play.json.collection.JSONCollection
import suite.MongoSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LockRepositorySpec extends FreeSpec with MustMatchers with MongoSuite
  with ScalaFutures with IntegrationPatience with OptionValues with MockitoSugar {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a lock repository" - {

    "must provide a lock for an id when that id is not already locked" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[LockRepository]

        repository.started.futureValue
        val result = repository.lock("id").futureValue

        result mustEqual true
      }
    }

    "must not provide a lock for an id when that id is already locked" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[LockRepository]

        repository.started.futureValue
        repository.lock("id").futureValue

        val result = repository.lock("id").futureValue

        result mustEqual false
      }
    }
  }
}
