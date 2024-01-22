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
import models.declarations.State
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AmendmentFailedSubmissionWorkerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with Constants {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = mock[DefaultDeclarationsRepository]
  val mockLockRepository: DefaultLockRepository                 = mock[DefaultLockRepository]

  val config: Configuration = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val amendmentFailedSubmissionWorker: AmendmentFailedSubmissionWorker = new AmendmentFailedSubmissionWorker(
      mockDeclarationsRepository,
      lockRepository = mockLockRepository,
      config = config
    )
  }

  "AmendmentFailedSubmissionWorker" when {
    ".tap" must {
      "retrieve amendments with an amendState SubmissionFailed and deletes them from the repository returning each Amendment removed" in new Setup {
        when(mockDeclarationsRepository.failedAmendments).thenReturn(Source(Vector(amendment)))
        when(mockLockRepository.lock(amendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockDeclarationsRepository.setAmendState(amendment.chargeReference, State.Paid))
          .thenReturn(Future.successful(amendment))

        await(amendmentFailedSubmissionWorker.tap.pull()) mustBe Some(amendment)
      }
    }
  }

}
