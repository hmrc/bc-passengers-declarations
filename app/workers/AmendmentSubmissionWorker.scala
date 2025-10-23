/*
 * Copyright 2025 HM Revenue & Customs
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

import connectors.HODConnector
import models.{CMASubmissionResponse, Response, SubmissionResponse}
import models.declarations.{Declaration, State}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import org.apache.pekko.stream.{ActorAttributes, Materializer}
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditingTools

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendmentSubmissionWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  hodConnector: HODConnector,
  config: Configuration,
  auditConnector: AuditConnector,
  auditingTools: AuditingTools
)(implicit mat: Materializer, ec: ExecutionContext)
    extends BaseDeclarationWorker {

  private val logger = Logger(this.getClass)

  private val initialDelay: FiniteDuration =
    durationValueFromConfig("workers.amendment-submission-worker.initial-delay", config)
  private val interval: FiniteDuration     = durationValueFromConfig("workers.amendment-submission-worker.interval", config)
  private val per: FiniteDuration          = durationValueFromConfig("workers.amendment-submission-worker.throttle.per", config)
  private val parallelism: Int             = config.get[Int]("workers.amendment-submission-worker.parallelism")
  private val elements: Int                = config.get[Int]("workers.amendment-submission-worker.throttle.elements")

  val tap: SinkQueueWithCancel[(Declaration, Response)] = {

    logger.info("[AmendmentSubmissionWorker][tap] Amendment submission worker started")

    Source
      .tick(initialDelay, interval, declarationsRepository.paidAmendmentsForEtmp)
      .flatMapConcat(identity)
      .throttle(elements, per)
      .mapAsync(parallelism)(getLock)
      .mapConcat(lockSuccessful)
      .mapAsync(parallelism) { declaration =>
        for {
          result <- hodConnector.submit(declaration, isAmendment = true)
          _      <- result match {
                      case SubmissionResponse.Submitted | CMASubmissionResponse.Submitted               =>
                        auditConnector.sendExtendedEvent(
                          auditingTools.buildDeclarationSubmittedDataEvent(declaration.amendData.get)
                        )
                        declarationsRepository.setAmendSentToEtmp(declaration.chargeReference, amendSentToEtmp = true)
                      case SubmissionResponse.Error | CMASubmissionResponse.Error                       =>
                        logger.error(
                          s"""[AmendmentSubmissionWorker][tap] PNGRS_DES_SUBMISSION_FAILURE call to DES (EIS) is failed.
                              |ChargeReference:  ${declaration.chargeReference},
                              |CorrelationId:  ${declaration.amendCorrelationId
                              .getOrElse("amendCorrelationId is not available in Mongo")}""".stripMargin.replace("\n", " ")
                        )
                        Future.successful(())
                      case SubmissionResponse.ParsingException | CMASubmissionResponse.ParsingException =>
                        Future.successful(())
                      case SubmissionResponse.Failed | CMASubmissionResponse.Failed                     =>
                        logger.error(
                          s"""[AmendmentSubmissionWorker][tap] PNGRS_DES_SUBMISSION_FAILURE BAD Request is received from DES (EIS)
                              |ChargeReference:  ${declaration.chargeReference},
                              |CorrelationId:  ${declaration.amendCorrelationId
                              .getOrElse("amendCorrelationId is not available in Mongo")}""".stripMargin.replace("\n", " ")
                        )
                        declarationsRepository.setAmendState(declaration.chargeReference, State.SubmissionFailed)
                    }
        } yield (declaration, result)
      }
      .mapAsync(parallelism) { response =>
        lockRepository.release(response._1.chargeReference.value)
        Future.successful(response)
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
}
