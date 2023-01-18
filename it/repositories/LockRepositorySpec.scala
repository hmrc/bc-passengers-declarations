package repositories

import helpers.IntegrationSpecCommonBase
import models.Lock
import org.mongodb.scala.Document
import play.api.inject.guice.GuiceApplicationBuilder
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.test.Helpers.{await, running}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.Helpers._


class LockRepositorySpec extends IntegrationSpecCommonBase with DefaultPlayMongoRepositorySupport[Lock] {

  override def repository = new DefaultLockRepository(mongoComponent)
  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes)
  }

  override def afterEach(): Unit = {
    super.afterEach()
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  "a lock repository" should  {

    "must provide a lock for an id when that id is not already locked" in {

      val app = builder.build()

      running(app) {

        val result = await(repository.lock(1))

        result mustEqual true
      }
    }

    "must not provide a lock for an id when that id is already locked" in {

      val app = builder.build()

      running(app) {

        repository.lock(0).futureValue

        val result = await(repository.lock(0))

        result mustEqual false
      }
    }

    "must ensure indices" in {

      val ttl = 123
      val app =
        builder
          .configure("locks.ttl" -> ttl)
          .build()

      running(app) {

        val indices : Seq[Document] = await(repository.collection.listIndexes().toFuture())

        indices.map(
          doc => {
            doc.toJson match {
              case json if json.contains("lastUpdated")  => json.contains("locks-index") mustEqual true
              case _ => doc.toJson.contains("_id") mustEqual true
            }
          }
        )
      }
    }

    "must return the lock status for an id" in {

      val app = builder.build()

      running(app) {

        await(repository.isLocked(0)) mustEqual false

        await(repository.lock(0))

        await(repository.isLocked(0)) mustEqual true
      }
    }

    "must release a lock" in {

      val app = builder.build()

      running(app) {

        await(repository.lock(0))

        await(repository.isLocked(0)) mustEqual true

        await(repository.release(0))

        await(repository.isLocked(0)) mustEqual false
      }
    }


/*    "must return a successful future if releasing a lock fails" in {



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

    }*/
  }

}
