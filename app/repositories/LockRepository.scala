/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package repositories

import javax.inject.{Inject, Singleton}
import models.Lock
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.LastError
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultLockRepository @Inject()(
                                mongo: ReactiveMongoApi,
                                config: Configuration
                              )(implicit ec: ExecutionContext) extends LockRepository {

  private val collectionName: String = "locks"

  private lazy val documentExistsErrorCode = Some(11000)

  private val cacheTtl = config.get[Int]("locks.ttl")

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val index = Index(
    key     = Seq("lastUpdated" -> IndexType.Ascending),
    name    = Some("locks-index"),
    options = BSONDocument("expireAfterSeconds" -> cacheTtl)
  )

  val started: Future[Unit] = {
    collection.flatMap {
      _.indexesManager.ensure(index)
    }.map(_ => ())
  }

  override def lock(id: Int): Future[Boolean] =
    collection
      .flatMap {
        _.insert(Lock(id))
          .map(_ => true)
      } recover {
      case e: LastError if e.code == documentExistsErrorCode =>
        false
      }

  override def release(id: Int): Future[Unit] =
    collection
      .flatMap {
        _.findAndRemove(Json.obj("_id" -> id))
          .map(_ => ())
      }.fallbackTo(Future.successful(()))

  override def isLocked(id: Int): Future[Boolean] =
    collection
      .flatMap{
        _.find(Json.obj("_id" -> id), None).one[Lock]
      }.map(_.isDefined)
}

trait LockRepository {

  val started: Future[Unit]
  def lock(id: Int): Future[Boolean]
  def release(id: Int): Future[Unit]
  def isLocked(id: Int): Future[Boolean]
}
