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

package helpers

import models.ChargeRefJsons.ChargeRefJson
import models.Lock
import models.declarations.Declaration
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions, ReplaceOneModel, ReplaceOptions}
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import org.mongodb.scala.SingleObservableFuture

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MongoTestUtils {

  def givenAnExistingDocument[A](
    document: A
  )(implicit inCollection: MongoCollection[A], documentType: DocumentType[A]): Unit = {

    val (selector, toInsert) = documentType.toSelectorAndInsert(document)

    val result = inCollection
      .findOneAndReplace(selector, toInsert, FindOneAndReplaceOptions().upsert(true))
      .toFuture()
      .flatMap(_ => Future.unit)

    await(result)
  }

  def givenExistingDocuments[A](
    documents: List[A]
  )(implicit inCollection: MongoCollection[A], documentType: DocumentType[A]): Unit = {
    val updates = documents.map { document =>
      val (selector, toInsert) = documentType.toSelectorAndInsert(document)
      ReplaceOneModel(selector, toInsert, ReplaceOptions().upsert(true))
    }

    val result = inCollection
      .bulkWrite(updates)
      .toFuture()
      .flatMap(_ => Future.unit)

    await(result)
  }

  def beEquivalentTo[A](expected: A)(implicit matcherType: MatcherType[A]): Matcher[A] = (result: A) => {
    val withoutLastUpdated = matcherType.withLastUpdated(result, matcherType.getLastUpdated(expected))
    val areEquivalent      = withoutLastUpdated == expected
    val explanation        = if (areEquivalent) "" else s"$expected was not equal to $result"
    MatchResult(areEquivalent, explanation, explanation)
  }
}

trait DocumentType[A] {
  def toSelectorAndInsert(document: A): (Bson, A)
}

object DocumentType {
  implicit val declarationType: DocumentType[Declaration] = new DocumentType[Declaration] {
    def toSelectorAndInsert(declaration: Declaration): (Bson, Declaration) =
      (Filters.equal("_id", declaration.chargeReference.toString), declaration)
  }

  implicit val lockType: DocumentType[Lock] = new DocumentType[Lock] {
    def toSelectorAndInsert(lock: Lock): (Bson, Lock) =
      (Filters.equal("_id", lock._id), lock)
  }

  implicit val chargeRefJsonType: DocumentType[ChargeRefJson] = new DocumentType[ChargeRefJson] {
    def toSelectorAndInsert(chargeRefJson: ChargeRefJson): (Bson, ChargeRefJson) =
      (Filters.equal("_id", "counter"), chargeRefJson)

  }
}

trait MatcherType[A] {
  def withLastUpdated(document: A, lastUpdated: LocalDateTime): A
  def getLastUpdated(document: A): LocalDateTime
}

object MatcherType {
  implicit val declarationType: MatcherType[Declaration] = new MatcherType[Declaration] {

    def withLastUpdated(declaration: Declaration, lastUpdated: LocalDateTime): Declaration =
      declaration.copy(lastUpdated = lastUpdated)

    def getLastUpdated(declaration: Declaration): LocalDateTime =
      declaration.lastUpdated
  }
}
