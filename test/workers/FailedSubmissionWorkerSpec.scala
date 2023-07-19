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
import models.declarations.State
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FailedSubmissionWorkerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with Constants {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = mock[DefaultDeclarationsRepository]
  val mockLockRepository: DefaultLockRepository                 = mock[DefaultLockRepository]

  val config: Configuration = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val failedSubmissionWorker: FailedSubmissionWorker = new FailedSubmissionWorker(
      mockDeclarationsRepository,
      lockRepository = mockLockRepository,
      config = config
    )
  }

  "FailedSubmissionWorkerSpec" when {
    ".tap" must {
      "retrieve declarations with a State SubmissionFailed and deletes them from the repository returning each Declaration removed" in new Setup {

        when(mockDeclarationsRepository.failedDeclarations).thenReturn(Source(Vector(declaration)))
        when(mockLockRepository.lock(declaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockDeclarationsRepository.setState(declaration.chargeReference, State.Paid))
          .thenReturn(Future.successful(declaration))

        await(failedSubmissionWorker.tap.pull()) mustBe Some(declaration)
      }
    }
  }

}
