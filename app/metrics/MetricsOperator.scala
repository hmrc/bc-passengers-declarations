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

package metrics

import com.codahale.metrics._
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject

class MetricsOperator @Inject() (val metrics: Metrics) {

  lazy val registry: MetricRegistry = metrics.defaultRegistry

  def startTimer(): Timer.Context             = registry.timer("submission-timer").time()
  def stopTimer(context: Timer.Context): Long = context.stop()

  def setCounter(name: String)(newCount: Int): Unit = {
    registry.remove(name)
    registry.counter(name).inc(newCount)

  }

  val setPendingPaymentCounter: Int => Unit   = setCounter("pending-payment-counter")
  val setPaymentCompleteCounter: Int => Unit  = setCounter("payment-complete-counter")
  val setPaymentFailedCounter: Int => Unit    = setCounter("payment-failed-counter")
  val setPaymentCancelledCounter: Int => Unit = setCounter("payment-cancelled-counter")
  val setFailedSubmissionCounter: Int => Unit = setCounter("failed-submission-counter")

}
