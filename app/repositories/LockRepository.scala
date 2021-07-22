/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
