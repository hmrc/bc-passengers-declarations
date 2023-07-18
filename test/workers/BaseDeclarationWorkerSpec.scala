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

import akka.stream.Supervision
import models.declarations.Declaration
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultLockRepository, LockRepository}
import util.Constants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class BaseDeclarationWorkerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with Constants {
  private val mockLockRepository: DefaultLockRepository = mock[DefaultLockRepository]
  private val mockConfiguration: Configuration = mock[Configuration]


  object BaseDeclarationWorkerTest extends BaseDeclarationWorker {
    override protected def lockRepository: LockRepository = mockLockRepository

    def testGetLock(declaration: Declaration): Future[(Boolean, Declaration)] =
      getLock(declaration)

    def testLockSuccessful(data: (Boolean, Declaration)): List[Declaration] =
      lockSuccessful(data)
}

  "BaseDeclarationWorkerSpec" - {
    "getLock" - {
      "returns a tuple when the lock is successful" in {
        when(mockLockRepository.lock(any[Int]())).thenReturn(Future.successful(true))
        await(BaseDeclarationWorkerTest.testGetLock(declaration)) mustBe (true, declaration)
      }
    }

    "lockSuccessful" - {
      "return the declaration in a list" in {
        BaseDeclarationWorkerTest.testLockSuccessful(true, declaration) mustBe List(declaration)
      }
      "returns Nil when the lockStatus is false" in {
        BaseDeclarationWorkerTest.testLockSuccessful(false, declaration) mustBe Nil
      }
    }

    "supervisionStrategy" - {
      "sets supervisionStrategy state to resume a worker queue when encountering a NonFatal Exception" in {

        val nonFatalException: Exception = new Exception("")

        BaseDeclarationWorkerTest.supervisionStrategy(nonFatalException) mustBe Supervision.resume
      }

      "sets supervisionStrategy state to stop a worker queue when encountering a Fatal Exception" in {

        val fatalException: VirtualMachineError = new VirtualMachineError{}

        BaseDeclarationWorkerTest.supervisionStrategy(fatalException) mustBe Supervision.stop
      }
    }

    "durationValueFromConfig" - {
      "returns a configuration value as a FiniteDuration" in {
        when(mockConfiguration.get[FiniteDuration]("test")).thenReturn(2.seconds)

        BaseDeclarationWorkerTest.durationValueFromConfig("test", mockConfiguration) mustBe 2.seconds
      }
      "returns a configuration value as a FiniteDuration if it is a String" in {
        when(mockConfiguration.get[String]("test")).thenReturn("3.seconds")

        BaseDeclarationWorkerTest.durationValueFromConfig("test", mockConfiguration) mustBe 3.seconds
      }
    }
  }
}
