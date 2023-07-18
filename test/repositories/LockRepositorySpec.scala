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

import models.Lock
import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.Constants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LockRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with DefaultPlayMongoRepositorySupport[Lock]
    with Constants {

  override protected val repository: DefaultLockRepository = new DefaultLockRepository(
    mongoComponent = mongoComponent
  )

  override lazy val checkTtlIndex: Boolean = false
  // each lock defines it's own expiry, so doesn't rely on ttl indexes

  "LockRepository" - {
    ".lock" in {
      await(repository.lock(1)) mustBe true
    }

    ".release" - {
      "finds a lock document and removes it from the database" in {
        givenAnExistingDocument(Lock(1))

        await(repository.release(1)) mustBe ()

        repository.collection.find(Filters.equal("_id", 1)).toFuture().futureValue mustBe Seq.empty[Lock]
      }

      "no matching lock documents found" in {
        val result: Unit = await(repository.release(1))
        result mustBe None.orNull.asInstanceOf[Seq[Lock]]
      }
    }

    ".isLocked" - {
      "returns true when a lock document is found" in {

        givenAnExistingDocument(Lock(1))
        await(repository.isLocked(1)) mustBe true
      }

      "returns false when no document found" in {
        await(repository.isLocked(1)) mustBe false
      }
    }

  }

  private def givenAnExistingDocument(lock: Lock): Unit = {
    val selector = Filters.equal("_id", lock._id)

    val result = repository.collection
      .findOneAndReplace(selector, lock, FindOneAndReplaceOptions().upsert(true))
      .toFuture()
      .flatMap(_ => Future.unit)

    await(result)
  }

}
