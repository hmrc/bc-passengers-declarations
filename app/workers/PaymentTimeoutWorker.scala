/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package workers

import java.time.{LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}

import javax.inject.{Inject, Singleton}
import models.declarations.Declaration
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class PaymentTimeoutWorker @Inject()(
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer)
  extends BaseDeclarationWorker {

    private val logger: Logger = Logger(this.getClass)

    private val initialDelayFromConfig = config.get[String]("workers.payment-timeout-worker.initial-delay").replace('.',' ')
    private val initialDelayFromConfigFiniteDuration = config.get[FiniteDuration]("workers.payment-timeout-worker.initial-delay")
    private val finiteInitialDelay = Duration(initialDelayFromConfig)
    private val initialDelay = Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)

    private val intervalFromConfig = config.get[String]("workers.payment-timeout-worker.interval").replace('.',' ')
    private val intervalFromConfigFiniteDuration = config.get[FiniteDuration]("workers.payment-timeout-worker.interval")
    private val finiteInterval = Duration(intervalFromConfig)
    private val interval = Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

    private val parallelism = config.get[Int]("workers.payment-timeout-worker.parallelism")

    private val paymentTimeoutFromConfig = config.get[String]("declarations.payment-no-response-timeout").replace('.',' ')
    private val paymentTimeoutFromConfigFiniteDuration = config.get[FiniteDuration]("declarations.payment-no-response-timeout")
    private val finitePaymentTimeout = Duration(paymentTimeoutFromConfig)
    private val paymentTimeout = Some(finitePaymentTimeout).collect { case d: FiniteDuration => d }.getOrElse(paymentTimeoutFromConfigFiniteDuration)


    private val supervisionStrategy: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[Declaration] = {

      logger.info("Payment timeout worker started")

      def timeout = LocalDateTime.now(ZoneOffset.UTC).minus(paymentTimeout.toMillis, ChronoUnit.MILLIS)

      Source.tick(initialDelay, interval, declarationsRepository.unpaidDeclarations)
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
