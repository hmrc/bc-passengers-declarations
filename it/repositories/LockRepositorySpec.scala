package repositories

import org.netcrusher.core.reactor.NioReactor
import org.netcrusher.tcp.TcpCrusherBuilder
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import reactivemongo.api.indexes.IndexType
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection
import suite.FailOnUnindexedQueries

import scala.concurrent.ExecutionContext.Implicits.global

class LockRepositorySpec extends FreeSpec with MustMatchers with FailOnUnindexedQueries
  with ScalaFutures with IntegrationPatience with OptionValues {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a lock repository" - {

    "must provide a lock for an id when that id is not already locked" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[LockRepository]

        started(app).futureValue

        val result = repository.lock(0).futureValue

        result mustEqual true
      }
    }

    "must not provide a lock for an id when that id is already locked" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[LockRepository]

        started(app).futureValue

        repository.lock(0).futureValue

        val result = repository.lock(0).futureValue

        result mustEqual false
      }
    }

    "must ensure indices" in {

      database.flatMap(_.drop()).futureValue

      val ttl = 123
      val app =
        builder
          .configure("locks.ttl" -> ttl)
          .build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val indices = database.flatMap {
          _.collection[JSONCollection]("locks")
            .indexesManager.list()
        }.futureValue

        indices.find {
          index =>
            index.name.contains("locks-index") &&
              index.key == Seq("lastUpdated" -> IndexType.Ascending) &&
              index.options == BSONDocument("expireAfterSeconds" -> ttl)
        } mustBe defined
      }
    }

    "must return the lock status for an id" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[LockRepository]

        started(app).futureValue

        repository.isLocked(0).futureValue mustEqual false

        repository.lock(0).futureValue

        repository.isLocked(0).futureValue mustEqual true
      }
    }

    "must release a lock" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[LockRepository]

        started(app).futureValue

        repository.lock(0).futureValue

        repository.isLocked(0).futureValue mustEqual true

        repository.release(0).futureValue

        repository.isLocked(0).futureValue mustEqual false
      }
    }

    "must return a successful future if releasing a lock fails" in {

      database.flatMap(_.drop()).futureValue

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

          val repository = app.injector.instanceOf[LockRepository]

          started(app).futureValue

          repository.lock(0).futureValue

          proxy.close()

          repository.release(0).futureValue

          proxy.open()

          repository.isLocked(0).futureValue mustEqual true
        }
      } finally {

        proxy.close()
        reactor.close()
      }

    }
  }
}
