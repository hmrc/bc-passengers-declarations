/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package workers

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import javax.inject.{Inject, Singleton}
import models.declarations.{Declaration, State}
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class FailedSubmissionWorker @Inject()(
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer)
  extends BaseDeclarationWorker {

    private val logger = Logger(this.getClass)

    private val parallelism = config.get[Int]("workers.failed-submission-worker.parallelism")

    private val decider: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[Declaration] = {

      logger.info("Failed submission worker started")

      declarationsRepository.failedDeclarations
        .mapAsync(parallelism)(getLock)
        .mapConcat(lockSuccessful)
        .mapAsync(parallelism) {
          declaration =>

            declarationsRepository.setState(declaration.chargeReference, State.Paid)
        }.wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
        .run()
    }
}
