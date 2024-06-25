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
import org.apache.pekko.stream.Supervision
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultLockRepository, LockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class BaseDeclarationWorkerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with Constants {
  private val mockLockRepository: DefaultLockRepository = Mockito.mock(classOf[DefaultLockRepository])
  private val mockConfiguration: Configuration          = Mockito.mock(classOf[Configuration])

  object BaseDeclarationWorkerTest extends BaseDeclarationWorker {
    override protected def lockRepository: LockRepository = mockLockRepository

    def testGetLock(declaration: Declaration): Future[(Boolean, Declaration)] =
      getLock(declaration)

    def testLockSuccessful(data: (Boolean, Declaration)): List[Declaration] =
      lockSuccessful(data)
  }

  "BaseDeclarationWorkerSpec" when {
    "getLock" must {
      "return a tuple when the lock is successful" in {
        when(mockLockRepository.lock(any[Int]())).thenReturn(Future.successful(true))
        await(BaseDeclarationWorkerTest.testGetLock(declaration)) mustBe (true, declaration)
      }
    }

    "lockSuccessful" must {
      "return the declaration in a list" in {
        BaseDeclarationWorkerTest.testLockSuccessful((true, declaration)) mustBe List(declaration)
      }
      "return Nil when the lockStatus is false" in {
        BaseDeclarationWorkerTest.testLockSuccessful((false, declaration)) mustBe Nil
      }
    }

    "supervisionStrategy" must {
      "set supervisionStrategy state to resume a worker queue when encountering a NonFatal Exception" in {

        val nonFatalException: Exception = new Exception("")

        BaseDeclarationWorkerTest.supervisionStrategy(nonFatalException) mustBe Supervision.resume
      }

      "set supervisionStrategy state to stop a worker queue when encountering a Fatal Exception" in {

        val fatalException: VirtualMachineError = new VirtualMachineError {}

        BaseDeclarationWorkerTest.supervisionStrategy(fatalException) mustBe Supervision.stop
      }
    }

    "durationValueFromConfig" must {
      "return a configuration value as a FiniteDuration" in {
        when(mockConfiguration.get[FiniteDuration]("test")).thenReturn(2.seconds)

        BaseDeclarationWorkerTest.durationValueFromConfig("test", mockConfiguration) mustBe 2.seconds
      }
      "return a configuration value as a FiniteDuration if it is a String" in {
        when(mockConfiguration.get[String]("test")).thenReturn("3.seconds")

        BaseDeclarationWorkerTest.durationValueFromConfig("test", mockConfiguration) mustBe 3.seconds
      }
    }
  }
}
