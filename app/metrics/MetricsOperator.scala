/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package metrics

import com.codahale.metrics._
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject

class MetricsOperator @Inject() (val metrics: Metrics) {

  type Metric = String

  lazy val registry = metrics.defaultRegistry

  def startTimer() = registry.timer("submission-timer").time()
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