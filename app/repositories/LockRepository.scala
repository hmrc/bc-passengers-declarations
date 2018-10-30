package repositories

import javax.inject.{Inject, Singleton}
import models.Lock
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.LastError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultLockRepository @Inject()(
                                mongo: ReactiveMongoApi,
                                config: Configuration
                              )(implicit ec: ExecutionContext) extends LockRepository {

  private val collectionName: String = "locks"

  private lazy val documentExistsErrorCode = Some(11000)

  private val cacheTtl = config.get[Int]("mongodb.collections.locks.ttl")

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val index = Index(
    key     = Seq("lastUpdated" -> IndexType.Ascending),
    name    = Some("locks-index"),
    options = BSONDocument("expireAfterSeconds" -> cacheTtl)
  )

  val started: Future[_] = {
    collection.flatMap {
      _.indexesManager.ensure(index)
    }
  }

  override def lock(id: String): Future[Boolean] =
    collection
      .flatMap {
        _.insert(Lock(id))
          .map(_ => true)
      } recover {
      case e: LastError if e.code == documentExistsErrorCode =>
        false
      }
}

trait LockRepository {

  val started: Future[_]
  def lock(id: String): Future[Boolean]
}
