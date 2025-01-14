/*
 * Copyright 2025 HM Revenue & Customs
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

import helpers.IntegrationSpecCommonBase
import models.Lock
import org.mongodb.scala.Document
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.mongodb.scala.ObservableFuture

import scala.concurrent.ExecutionContext.Implicits.global

class LockRepositoryISpec extends IntegrationSpecCommonBase with DefaultPlayMongoRepositorySupport[Lock] {

  override val repository = new DefaultLockRepository(mongoComponent)

  override lazy val checkTtlIndex: Boolean = false // each lock defines it's own expiry, so doesn't rely on ttl indexes

  "a lock repository" should {

    "must provide a lock for an id when that id is not already locked" in {

      val result = await(repository.asInstanceOf[LockRepository].lock(1))

      result shouldBe true
    }

    "must not provide a lock for an id when that id is already locked" in {

      repository.asInstanceOf[LockRepository].lock(0).futureValue

      val result = await(repository.asInstanceOf[LockRepository].lock(0))

      result shouldBe false
    }

    "must ensure indices" in {

      val indices: Seq[Document] = await(repository.collection.listIndexes().toFuture())

      indices.map { doc =>
        doc.toJson() match {
          case json if json.contains("lastUpdated") => json.contains("locks-index") shouldBe true
          case _                                    => doc.toJson().contains("_id") shouldBe true
        }
      }
    }

    "must return the lock status for an id" in {

      await(repository.asInstanceOf[LockRepository].isLocked(0)) shouldBe false

      await(repository.asInstanceOf[LockRepository].lock(0))

      await(repository.asInstanceOf[LockRepository].isLocked(0)) shouldBe true
    }

    "must release a lock" in {

      await(repository.asInstanceOf[LockRepository].lock(0))

      await(repository.asInstanceOf[LockRepository].isLocked(0)) shouldBe true

      await(repository.asInstanceOf[LockRepository].release(0))

      await(repository.asInstanceOf[LockRepository].isLocked(0)) shouldBe false
    }
  }
}
