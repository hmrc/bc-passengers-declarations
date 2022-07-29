/*
 * Copyright 2022 HM Revenue & Customs
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

  private val initialDelayFromConfig = config.get[String]("workers.amendment-submission-worker.initial-delay").replace('.',' ')
  private val initialDelayFromConfigFiniteDuration = config.get[FiniteDuration]("workers.amendment-submission-worker.initial-delay")
  private val finiteInitialDelay = Duration(initialDelayFromConfig)
  private val initialDelay = Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)

  private val intervalFromConfig = config.get[String]("workers.amendment-submission-worker.interval").replace('.',' ')
  private val intervalFromConfigFiniteDuration = config.get[FiniteDuration]("workers.amendment-submission-worker.interval")
  private val finiteInterval = Duration(intervalFromConfig)
  private val interval = Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

  private val parallelism = config.get[Int]("workers.amendment-submission-worker.parallelism")
  private val elements = config.get[Int]("workers.amendment-submission-worker.throttle.elements")

  private val perFromConfig = config.get[String]("workers.amendment-submission-worker.throttle.per").replace('.',' ')
  private val perFromConfigFiniteDuration = config.get[FiniteDuration]("workers.amendment-submission-worker.throttle.per")
  private val finitePer = Duration(perFromConfig)
  private val per = Some(finitePer).collect { case d: FiniteDuration => d }.getOrElse(perFromConfigFiniteDuration)

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
                  auditConnector.sendExtendedEvent(auditingTools.buildDeclarationSubmittedDataEvent(declaration.amendData.get))
                  declarationsRepository.setAmendSentToEtmp(declaration.chargeReference,amendSentToEtmp = true)
                case SubmissionResponse.Error =>
                  logger.error(s"PNGRS_DES_SUBMISSION_FAILURE  [AmendmentSubmissionWorker] call to DES (EIS) is failed. ChargeReference:  ${declaration.chargeReference}, CorrelationId :  ${declaration.amendCorrelationId.getOrElse("amendCorrelationId is not available in Mongo")}")
                  Future.successful(())
                case SubmissionResponse.ParsingException =>
                  Future.successful(())
                case SubmissionResponse.Failed =>
                  logger.error(s"PNGRS_DES_SUBMISSION_FAILURE  [AmendmentSubmissionWorker] BAD Request is received from DES (EIS) ChargeReference:  ${declaration.chargeReference}, CorrelationId :  ${declaration.amendCorrelationId.getOrElse("amendCorrelationId is not available in Mongo")}")
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

