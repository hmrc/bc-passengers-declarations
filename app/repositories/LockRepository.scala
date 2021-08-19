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
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.mongodb.scala.model.Indexes.{ascending}
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}


@Singleton
  class DefaultLockRepository @Inject()(
                                         mongoComponent: MongoComponent,
                                         config: Configuration
                                       )(implicit ec: ExecutionContext) extends PlayMongoRepository[Lock](
  collectionName = "locks",
  mongoComponent = mongoComponent,
  domainFormat   = Lock.formats,
  indexes = Seq(
    IndexModel(ascending("lastUpdated"), IndexOptions().name("locks-index")
      .expireAfter(300, TimeUnit.SECONDS)),
  )
  ) with LockRepository {


  val started: Future[Unit] = {
    null
  }

  override def lock(id: Int): Future[Boolean] = collection.insertOne(Lock(id)).toFuture().map(_ => true).fallbackTo(Future.successful(false))

  override def release(id: Int): Future[Unit] = collection.findOneAndDelete(equal("_id", Codecs.toBson(id))).map(_ => ()).head().fallbackTo(Future.successful(()))

  override def isLocked(id: Int): Future[Boolean] = collection.find(equal("_id", Codecs.toBson(id)))
    .first().toFuture().map {
    case locks if locks != null => true
    case _ =>  false
  }
}


trait LockRepository {

  val started: Future[Unit]
  def lock(id: Int): Future[Boolean]
  def release(id: Int): Future[Unit]
  def isLocked(id: Int): Future[Boolean]
}
