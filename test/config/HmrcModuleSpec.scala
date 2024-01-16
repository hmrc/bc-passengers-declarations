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

package config

import akka.pattern.CircuitBreaker
import org.mockito.MockitoSugar.mock
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.Binding
import play.api.{Configuration, Environment}
import repositories._
import services.ChargeReferenceService
import workers._

class HmrcModuleSpec extends AnyWordSpec with Matchers {

  private val mockConfiguration: Configuration = mock[Configuration]
  private val mockEnvironment: Environment     = mock[Environment]

  private val bindings: Seq[Binding[_]] = new HmrcModule().bindings(mockEnvironment, mockConfiguration)

  "HmrcModule" when {
    ".bindings" must {
      def test(scenario: String, clazz: Class[_]): Unit =
        s"bind the $scenario eagerly" in {
          bindings.filter(binding => binding.key.clazz == clazz).head.eager mustBe true
        }

      val input: Seq[(String, Class[_])] = Seq(
        ("DeclarationsRepository", classOf[DeclarationsRepository]),
        ("ChargeReferenceService", classOf[ChargeReferenceService]),
        ("LockRepository", classOf[LockRepository]),
        ("DeclarationSubmissionWorker", classOf[DeclarationSubmissionWorker]),
        ("AmendmentSubmissionWorker", classOf[AmendmentSubmissionWorker]),
        ("PaymentTimeoutWorker", classOf[PaymentTimeoutWorker]),
        ("AmendmentPaymentTimeoutWorker", classOf[AmendmentPaymentTimeoutWorker]),
        ("DeclarationDeletionWorker", classOf[DeclarationDeletionWorker]),
        ("FailedSubmissionWorker", classOf[FailedSubmissionWorker]),
        ("AmendmentFailedSubmissionWorker", classOf[AmendmentFailedSubmissionWorker]),
        ("MetricsWorker", classOf[MetricsWorker])
      )
      input.foreach(args => (test _).tupled(args))

      "not bind the CircuitBreaker eagerly" in {
        bindings.filter(binding => binding.key.clazz == classOf[CircuitBreaker]).head.eager mustBe false
      }
    }
  }
}
