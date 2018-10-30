package repositories

import javax.inject.{Inject, Singleton}
import models.Lock
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.LastError
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultLockRepository @Inject()(
                                mongo: ReactiveMongoApi
                              )(implicit ec: ExecutionContext) extends LockRepository {


  private val collectionName: String = "locks"

  lazy val documentExistsErrorCode = Some(11000)

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  override val started: Future[_] = Future.successful(())

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
