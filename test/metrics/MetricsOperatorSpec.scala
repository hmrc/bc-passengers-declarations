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

package metrics

import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.play.bootstrap.metrics.MetricsImpl

class MetricsOperatorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val metrics: MetricsImpl             = app.injector.instanceOf[MetricsImpl]
  val metricsOperator: MetricsOperator = new MetricsOperator(metrics)

  "MetricsOperator" when {
    ".startTimer" in {
      metricsOperator.startTimer()                               shouldBe a[Timer.Context]
      metricsOperator.registry.getTimers.get("submission-timer") shouldBe a[Timer]
    }

    ".stopTimer" in {
      val context: Context  = metricsOperator.registry.timer("test").time()
      val elapsedTime: Long = metricsOperator.stopTimer(context)

      elapsedTime.toInt should be > 0
    }

    ".setCounter" must {
      "set a counter up in metrics" in {
        metricsOperator.setCounter("test")(1)             shouldBe ()
        metricsOperator.registry.counter("test").getCount shouldBe 1
      }

      "reset the counter when the same counter is accessed" in {
        metricsOperator.setCounter("test")(1)             shouldBe ()
        metricsOperator.registry.counter("test").getCount shouldBe 1
        metricsOperator.setCounter("test")(3)             shouldBe ()
        metricsOperator.registry.counter("test").getCount shouldBe 3
      }
    }

    "setPendingPaymentCounter" in {
      metricsOperator.setPendingPaymentCounter(1)                          shouldBe ()
      metricsOperator.registry.counter("pending-payment-counter").getCount shouldBe 1
    }

    "setPaymentCompleteCounter" in {
      metricsOperator.setPaymentCompleteCounter(1)                          shouldBe ()
      metricsOperator.registry.counter("payment-complete-counter").getCount shouldBe 1
    }

    "setPaymentFailedCounter" in {
      metricsOperator.setPaymentFailedCounter(1)                          shouldBe ()
      metricsOperator.registry.counter("payment-failed-counter").getCount shouldBe 1
    }

    "setPaymentCancelledCounter" in {
      metricsOperator.setPaymentCancelledCounter(1)                          shouldBe ()
      metricsOperator.registry.counter("payment-cancelled-counter").getCount shouldBe 1
    }

    "setFailedSubmissionCounter" in {
      metricsOperator.setFailedSubmissionCounter(1)                          shouldBe ()
      metricsOperator.registry.counter("failed-submission-counter").getCount shouldBe 1
    }
  }

}
