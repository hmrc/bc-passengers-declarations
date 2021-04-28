/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package workers

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import javax.inject.{Inject, Singleton}
import models.declarations.Declaration
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class AmendmentPaymentTimeoutWorker @Inject()(
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer)
  extends BaseDeclarationWorker {

    private val logger: Logger = Logger(this.getClass)

    private val initialDelay = config.get[FiniteDuration]("workers.amendment-payment-timeout-worker.initial-delay")
    private val interval = config.get[FiniteDuration]("workers.amendment-payment-timeout-worker.interval")
    private val parallelism = config.get[Int]("workers.amendment-payment-timeout-worker.parallelism")
    private val paymentTimeout = config.get[Duration]("declarations.payment-no-response-timeout")


    private val supervisionStrategy: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[Declaration] = {

      logger.info(" Amendment payment timeout worker started")

      def timeout = LocalDateTime.now.minus(paymentTimeout.toMillis, ChronoUnit.MILLIS)

      Source.tick(initialDelay, interval, declarationsRepository.unpaidAmendments)
        .flatMapConcat(identity)
        .collect {
          case declaration if declaration.lastUpdated.isBefore(timeout) => declaration
        }
        .mapAsync(parallelism)(getLock)
        .mapConcat(lockSuccessful)
        .map {
          declaration =>

            logger.info(s"Declaration ${declaration.chargeReference.value} is stale, deleting")

            declarationsRepository.remove(declaration.chargeReference)

            declaration
        }.wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
        .run()
    }
}
