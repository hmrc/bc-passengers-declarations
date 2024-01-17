/*
 * Copyright 2024 HM Revenue & Customs
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

import helpers.Constants
import models.declarations.Declaration
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
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

class PaymentTimeoutWorkerSpec
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
    lazy val paymentTimeoutWorker: PaymentTimeoutWorker = new PaymentTimeoutWorker(
      mockDeclarationsRepository,
      lockRepository = mockLockRepository,
      config = config
    )
  }

  "PaymentTimeoutWorker" when {
    ".tap" must {
      "retrieve unpaid declarations with a State relating to Payment and deletes them from the repository returning each Declaration removed" in new Setup {

        val expiredDate: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).minus(15.days.toMillis, ChronoUnit.MILLIS)
        val outDated: Declaration      = declaration.copy(lastUpdated = expiredDate)

        when(mockDeclarationsRepository.unpaidDeclarations).thenReturn(Source(Vector(outDated)))
        when(mockLockRepository.lock(outDated.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockDeclarationsRepository.remove(outDated.chargeReference)).thenReturn(Future.successful(Some(outDated)))

        await(paymentTimeoutWorker.tap.pull()) mustBe Some(outDated)
      }

    }
  }

}
