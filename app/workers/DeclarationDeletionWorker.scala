/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package workers

import java.time.LocalDateTime

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import com.google.inject.{Inject, Singleton}
import models.declarations.Declaration
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class DeclarationDeletionWorker @Inject()(
                                           declarationsRepository: DeclarationsRepository,
                                           override protected val lockRepository: LockRepository,
                                           config: Configuration,
                                         )(implicit mat: Materializer, ec: ExecutionContext)
  extends BaseDeclarationWorker {

  private val logger = Logger(this.getClass)

  private val initialDelayFromConfig = config.get[String]("workers.declaration-deletion-worker.initial-delay").replace('.',' ')
  private val initialDelayFromConfigFiniteDuration = config.get[FiniteDuration]("workers.declaration-deletion-worker.initial-delay")
  private val finiteInitialDelay = Duration(initialDelayFromConfig)
  private val initialDelay = Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)

  private val intervalFromConfig = config.get[String]("workers.declaration-deletion-worker.interval").replace('.',' ')
  private val intervalFromConfigFiniteDuration = config.get[FiniteDuration]("workers.declaration-deletion-worker.interval")
  private val finiteInterval = Duration(intervalFromConfig)
  private val interval = Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

  private val parallelism = config.get[Int]("workers.declaration-deletion-worker.parallelism")

  private val timeToHoldFromConfig = config.get[String]("workers.declaration-deletion-worker.timeToHold").replace('.',' ')
  private val timeToHoldFromConfigFiniteDuration = config.get[FiniteDuration]("workers.declaration-deletion-worker.timeToHold")
  private val finiteTimeToHold = Duration(timeToHoldFromConfig)
  private val timeToHold = Some(finiteTimeToHold).collect { case d: FiniteDuration => d }.getOrElse(timeToHoldFromConfigFiniteDuration)

  private val supervisionStrategy: Supervision.Decider = {
    case NonFatal(_) => Supervision.resume
    case _           => Supervision.stop
  }

  val tap: SinkQueueWithCancel[Declaration] = {
    logger.info("Declaration deletion worker started")
    Source.tick(initialDelay, interval, declarationsRepository.paidDeclarationsForDeletion)
      .flatMapConcat(identity)
      .collect {
        case declaration if checkDeleteCondition(declaration) => declaration
      }
      .mapAsync(parallelism)(getLock)
      .mapConcat(lockSuccessful)
      .map {
        declaration =>
          logger.info(s"[DeclarationDeletionWorker][tap] Declaration ${declaration.chargeReference.value} is Paid, sent to ETMP, and crossed the time to hold, hence deleting")
          declarationsRepository.remove(declaration.chargeReference)
          declaration
      }.wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
  def checkDeleteCondition(declaration : Declaration) : Boolean = {
    declaration.lastUpdated.plusMinutes(timeToHold.toMinutes).isBefore(LocalDateTime.now())
  }
}

