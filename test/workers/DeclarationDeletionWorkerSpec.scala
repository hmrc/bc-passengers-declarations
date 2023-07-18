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

package workers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import models.declarations.Declaration
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import util.Constants

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DeclarationDeletionWorkerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with Constants {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = mock[DefaultDeclarationsRepository]
  val mockLockRepository: DefaultLockRepository                 = mock[DefaultLockRepository]

  val config: Configuration = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val declarationDeletionWorker: DeclarationDeletionWorker = new DeclarationDeletionWorker(
      declarationsRepository = mockDeclarationsRepository,
      lockRepository = mockLockRepository,
      config = config
    )
  }

  "DeclarationDeletionWorker" - {
    "tap" - {
      "successfully retrieves all stale declarations and deletes them from the repository returning each Declaration removed" in new Setup {

        val expiredDate: String = LocalDateTime
          .now(ZoneOffset.UTC)
          .minus(15.days.toMillis, ChronoUnit.MILLIS)
          .format(
            DateTimeFormatter
              .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
          )

        val dataWithInvalidReceiptDate: JsObject = declarationData.deepMerge(
          Json.obj(
            "simpleDeclarationRequest" -> Json.obj(
              "requestCommon" -> Json.obj(
                "receiptDate" -> expiredDate
              )
            )
          )
        )

        val invalidDeclaration: Declaration = declaration.copy(data = dataWithInvalidReceiptDate)

        val queuedDeclaration: Declaration = declaration.copy(randomChargeReference(), data = dataWithInvalidReceiptDate)

        when(mockDeclarationsRepository.paidDeclarationsForDeletion).thenReturn(Source(Vector(invalidDeclaration, queuedDeclaration)))

        when(mockLockRepository.lock(invalidDeclaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockDeclarationsRepository.remove(invalidDeclaration.chargeReference))
          .thenReturn(Future.successful(Some(invalidDeclaration)))
        when(mockDeclarationsRepository.remove(queuedDeclaration.chargeReference))
          .thenReturn(Future.successful(Some(queuedDeclaration)))

        //It is setup to remove all declarations found in a single pull() but will only return the first declaration it deletes
        await(declarationDeletionWorker.tap.pull()) mustBe Some(invalidDeclaration)
      }
    }
  }
}
