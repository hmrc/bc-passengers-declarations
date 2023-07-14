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

package services

import akka.util.Timeout
import models.ChargeRefJsons.ChargeRefJson
import models.ChargeReference
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.await
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ChargeReferenceServiceSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with CleanMongoCollectionSupport
    with ScalaFutures {
  override lazy val app: Application = new GuiceApplicationBuilder().build()

  implicit val timeout: Timeout = Timeout(5.seconds)

  lazy val chargeReferenceService = new SequentialChargeReferenceService(mongoComponent = mongoComponent)

  protected def collection: MongoCollection[ChargeRefJson] = chargeReferenceService.collection

  "ChargeReferenceService" - {
    ".nextChargeReference" - {
      "calculates the next ChargeReference id based on the last charge reference recorded" in {
        givenAnExistingDocument(ChargeRefJson("counter", 2))
        await(chargeReferenceService.nextChargeReference()) mustBe ChargeReference(3)
      }
    }
  }

  private def givenAnExistingDocument(chargeRefJson: ChargeRefJson): Unit = {
    val selector = Filters.equal("_id", "counter")

    val result = collection
      .findOneAndReplace(selector, chargeRefJson, FindOneAndReplaceOptions().upsert(true))
      .toFuture()
      .flatMap(_ => Future.unit)

    await(result)
  }
}
