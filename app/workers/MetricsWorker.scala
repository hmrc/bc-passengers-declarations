/*
 * Copyright 2020 HM Revenue & Customs
 *
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

  private val initialDelay = config.get[FiniteDuration]("workers.metrics-worker.initial-delay")
  private val interval = config.get[FiniteDuration]("workers.metrics-worker.interval")

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
