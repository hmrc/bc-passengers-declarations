/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package workers

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import com.google.inject.{Inject, Singleton}
import connectors.HODConnector
import models.SubmissionResponse
import models.declarations.{Declaration, State}
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditingTools

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class AmendmentSubmissionWorker @Inject()(
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  hodConnector: HODConnector,
  config: Configuration,
  auditConnector: AuditConnector,
  auditingTools: AuditingTools
)(implicit mat: Materializer, ec: ExecutionContext)
  extends BaseDeclarationWorker {

    private val logger = Logger(this.getClass)

    private val initialDelay = config.get[FiniteDuration]("workers.amendment-submission-worker.initial-delay")
    private val interval = config.get[FiniteDuration]("workers.amendment-submission-worker.interval")
    private val parallelism = config.get[Int]("workers.amendment-submission-worker.parallelism")
    private val elements = config.get[Int]("workers.amendment-submission-worker.throttle.elements")
    private val per = config.get[FiniteDuration]("workers.amendment-submission-worker.throttle.per")

    private val supervisionStrategy: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[(Declaration, SubmissionResponse)] = {

      logger.info("Amendment submission worker started")

      Source.tick(initialDelay, interval, declarationsRepository.paidAmendmentsForEtmp)
        .flatMapConcat(identity)
        .throttle(elements, per)
        .mapAsync(parallelism)(getLock)
        .mapConcat(lockSuccessful)
        .mapAsync(parallelism) {
          declaration => {
            for {
              result <- hodConnector.submit(declaration, true)
              _      <- result match {
                case SubmissionResponse.Submitted =>
                  auditConnector.sendExtendedEvent(auditingTools.buildDeclarationSubmittedDataEvent(declaration))
                  declarationsRepository.setAmendSentToEtmp(declaration.chargeReference,amendSentToEtmp = true)
                case SubmissionResponse.Error =>
                  Logger.error("PNGRS_DES_SUBMISSION_FAILURE [AmendmentSubmissionWorker] [SinkQueueWithCancel] Call to DES failed with 5XX")
                  Future.successful(())
                case SubmissionResponse.Failed =>
                  Logger.error("PNGRS_DES_SUBMISSION_FAILURE [AmendmentSubmissionWorker] [SinkQueueWithCancel] Call to DES failed with 400")
                  declarationsRepository.setAmendState(declaration.chargeReference, State.SubmissionFailed)
              }
            } yield (declaration, result)
          }
        }.mapAsync(parallelism){response => {
            lockRepository.release(response._1.chargeReference.value)
            Future.successful(response)
          }
        }
        .wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
        .run()
    }
}
