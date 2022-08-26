/*
 * Copyright 2022 HM Revenue & Customs
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

import com.codahale.metrics._
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject

class MetricsOperator @Inject() (val metrics: Metrics) {

  type Metric = String

  lazy val registry = metrics.defaultRegistry

  def startTimer()                      = registry.timer("submission-timer").time()
  def stopTimer(context: Timer.Context) = context.stop()

  def setCounter(name: String)(newCount: Int): Unit = {
    registry.remove(name)
    registry.counter(name).inc(newCount)

  }

  val setPendingPaymentCounter   = setCounter("pending-payment-counter") _
  val setPaymentCompleteCounter  = setCounter("payment-complete-counter") _
  val setPaymentFailedCounter    = setCounter("payment-failed-counter") _
  val setPaymentCancelledCounter = setCounter("payment-cancelled-counter") _
  val setFailedSubmissionCounter = setCounter("failed-submission-counter") _

}
