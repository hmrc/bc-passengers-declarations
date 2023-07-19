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
import helpers.Constants
import models.declarations.Declaration
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class AmendmentPaymentTimeoutWorkerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with Constants {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = mock[DefaultDeclarationsRepository]
  val mockLockRepository: DefaultLockRepository                 = mock[DefaultLockRepository]

  val config: Configuration = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val amendmentPaymentTimeoutWorker: AmendmentPaymentTimeoutWorker = new AmendmentPaymentTimeoutWorker(
      mockDeclarationsRepository,
      lockRepository = mockLockRepository,
      config = config
    )
  }

  "AmendmentPaymentTimeoutWorker" when {
    ".tap" must {
      "retrieve unpaid amendments with an amendState relating to Payment and deletes them from the repository returning each Amendment removed" in new Setup {

        val expiredDate: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).minus(15.days.toMillis, ChronoUnit.MILLIS)
        val outDated: Declaration      = amendment.copy(lastUpdated = expiredDate)

        when(mockDeclarationsRepository.unpaidAmendments).thenReturn(Source(Vector(outDated)))
        when(mockLockRepository.lock(amendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockDeclarationsRepository.remove(amendment.chargeReference)).thenReturn(Future.successful(Some(outDated)))

        await(amendmentPaymentTimeoutWorker.tap.pull()) mustBe Some(outDated)
      }
    }
  }

}
