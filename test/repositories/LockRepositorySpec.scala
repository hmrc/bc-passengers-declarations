/*
 * Copyright 2023 HM Revenue & Customs
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

import helpers.Constants
import helpers.MongoTestUtils.givenAnExistingDocument
import models.Lock
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class LockRepositorySpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with DefaultPlayMongoRepositorySupport[Lock]
    with Constants {

  override protected val repository: DefaultLockRepository = new DefaultLockRepository(
    mongoComponent = mongoComponent
  )

  override lazy val checkTtlIndex: Boolean = false
  // each lock defines it's own expiry, so doesn't rely on ttl indexes

  implicit val inCollection: MongoCollection[Lock] = repository.collection

  "LockRepository" when {
    ".lock" in {
      await(repository.lock(1)) mustBe true
    }

    ".release" must {
      "find a lock document and removes it from the database" in {
        givenAnExistingDocument(Lock(1))

        await(repository.release(1)) mustBe ()

        repository.collection.find(Filters.equal("_id", 1)).toFuture().futureValue mustBe Seq.empty[Lock]
      }

      "find no matching lock documents" in {
        val result: Unit = await(repository.release(1))
        result mustBe None.orNull.asInstanceOf[Seq[Lock]]
      }
    }

    ".isLocked" must {
      "return true when a lock document is found" in {
        givenAnExistingDocument(Lock(1))

        await(repository.isLocked(1)) mustBe true
      }

      "return false when no document found" in {
        await(repository.isLocked(1)) mustBe false
      }
    }
  }
}
