/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.{Done, NotUsed}
import akka.actor.Cancellable
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, Materializer, OverflowStrategy, Supervision}
import com.google.inject.{Inject, Singleton}
import metrics.MetricsOperator
import models.DeclarationsStatus
import play.api.{Configuration, Logger}
import repositories.DeclarationsRepository

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class MetricsWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  config: Configuration,
  metricsOperator: MetricsOperator
)(implicit mat: Materializer, ec: ExecutionContext) {

  private val logger = Logger(this.getClass)
  
  private val initialDelayFromConfig = config.get[String]("workers.metrics-worker.initial-delay").replace('.',' ')
  private val initialDelayFromConfigFiniteDuration = config.get[FiniteDuration]("workers.metrics-worker.initial-delay")
  private val finiteInitialDelay = Duration(initialDelayFromConfig)
  private val initialDelay = Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)

  private val intervalFromConfig = config.get[String]("workers.metrics-worker.interval").replace('.',' ')
  private val intervalFromConfigFiniteDuration = config.get[FiniteDuration]("workers.metrics-worker.interval")
  private val finiteInterval = Duration(intervalFromConfig)
  private val interval = Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

  private val supervisionStrategy: Supervision.Decider = {
    case NonFatal(e) =>
      logger.warn("Exception thrown in metrics stream, resuming", e)
      Supervision.resume
    case _ =>
      Supervision.stop
  }

  val tap: SinkQueueWithCancel[DeclarationsStatus] = {

    logger.info("Metrics worker started")

    Source.tick(initialDelay, interval, declarationsRepository.metricsCount)
      .flatMapConcat(identity)
      .map {
        status =>
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
