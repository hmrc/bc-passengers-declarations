/*
 * Copyright 2024 HM Revenue & Customs
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

import models.declarations.{Declaration, State}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel}
import org.apache.pekko.stream.{ActorAttributes, Materializer}
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AmendmentFailedSubmissionWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer)
    extends BaseDeclarationWorker {

  private val logger = Logger(this.getClass)

  private val parallelism: Int = config.get[Int]("workers.amendment-failed-submission-worker.parallelism")

  val tap: SinkQueueWithCancel[Declaration] = {

    logger.info("[AmendmentFailedSubmissionWorker][tap] Amendment failed submission worker started")

    declarationsRepository.failedAmendments
      .mapAsync(parallelism)(getLock)
      .mapConcat(lockSuccessful)
      .mapAsync(parallelism) { declaration =>
        declarationsRepository.setAmendState(declaration.chargeReference, State.Paid)
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
}
