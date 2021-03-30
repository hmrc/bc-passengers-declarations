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
class DeclarationSubmissionWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  hodConnector: HODConnector,
  config: Configuration,
  auditConnector: AuditConnector,
  auditingTools: AuditingTools
)(implicit mat: Materializer, ec: ExecutionContext)
  extends BaseDeclarationWorker {

    private val logger = Logger(this.getClass)

    private val initialDelayFromConfig = config.get[String]("workers.declaration-submission-worker.initial-delay").replace('.',' ')
    private val initialDelayFromConfigFiniteDuration = config.get[FiniteDuration]("workers.declaration-submission-worker.initial-delay")
    private val finiteInitialDelay = Duration(initialDelayFromConfig)
    private val initialDelay = Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)

    private val intervalFromConfig = config.get[String]("workers.declaration-submission-worker.interval").replace('.',' ')
    private val intervalFromConfigFiniteDuration = config.get[FiniteDuration]("workers.declaration-submission-worker.interval")
    private val finiteInterval = Duration(intervalFromConfig)
    private val interval = Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

    private val parallelism = config.get[Int]("workers.declaration-submission-worker.parallelism")
    private val elements = config.get[Int]("workers.declaration-submission-worker.throttle.elements")

    private val perFromConfig = config.get[String]("workers.declaration-submission-worker.throttle.per").replace('.',' ')
    private val perFromConfigFiniteDuration = config.get[FiniteDuration]("workers.declaration-submission-worker.throttle.per")
    private val finitePer = Duration(perFromConfig)
    private val per = Some(finitePer).collect { case d: FiniteDuration => d }.getOrElse(perFromConfigFiniteDuration)

    private val supervisionStrategy: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[(Declaration, SubmissionResponse)] = {

      logger.info("Declaration submission worker started")

      Source.tick(initialDelay, interval, declarationsRepository.paidDeclarationsForEtmp)
        .flatMapConcat(identity)
        .throttle(elements, per)
        .mapAsync(parallelism)(getLock)
        .mapConcat(lockSuccessful)
        .mapAsync(parallelism) {
          declaration => {
            for {
              result <- hodConnector.submit(declaration,false)
              _      <- result match {
                case SubmissionResponse.Submitted =>
                  auditConnector.sendExtendedEvent(auditingTools.buildDeclarationSubmittedDataEvent(declaration))
                  declarationsRepository.setSentToEtmp(declaration.chargeReference,sentToEtmp = true)
                case SubmissionResponse.Error =>
                  Logger.error("PNGRS_DES_SUBMISSION_FAILURE [DeclarationSubmissionWorker] [SinkQueueWithCancel] Call to DES failed with 5XX")
                  Future.successful(())
                case SubmissionResponse.Failed =>
                  Logger.error("PNGRS_DES_SUBMISSION_FAILURE [DeclarationSubmissionWorker] [SinkQueueWithCancel] Call to DES failed with 400")
                  declarationsRepository.setState(declaration.chargeReference, State.SubmissionFailed)
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

