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

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer}
import metrics.MetricsOperator
import models.DeclarationsStatus
import play.api.{Configuration, Logger}
import repositories.DeclarationsRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

@Singleton
class MetricsWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  config: Configuration,
  metricsOperator: MetricsOperator
)(implicit mat: Materializer)
    extends WorkerConfig {

  private val logger = Logger(this.getClass)

  private val initialDelay: FiniteDuration = durationValueFromConfig("workers.metrics-worker.initial-delay", config)
  private val interval: FiniteDuration     = durationValueFromConfig("workers.metrics-worker.interval", config)

  val tap: SinkQueueWithCancel[DeclarationsStatus] = {

    logger.info("Metrics worker started")

    Source
      .tick(initialDelay, interval, declarationsRepository.metricsCount)
      .flatMapConcat(identity)
      .map { status =>
        metricsOperator.setPendingPaymentCounter(status.pendingPaymentCount)
        metricsOperator.setPaymentCompleteCounter(status.paymentCompleteCount)
        metricsOperator.setPaymentFailedCounter(status.paymentFailedCount)
        metricsOperator.setPaymentCancelledCounter(status.paymentCancelledCount)
        metricsOperator.setFailedSubmissionCounter(status.failedSubmissionCount)
        status
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
}
