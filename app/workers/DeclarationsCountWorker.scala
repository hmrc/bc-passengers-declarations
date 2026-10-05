/*
 * Copyright 2026 HM Revenue & Customs
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

import models.DeclarationsCount
import org.apache.pekko.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import org.apache.pekko.stream.{ActorAttributes, Materializer}
import play.api.{Configuration, Logger}
import repositories.DeclarationsRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

@Singleton
class DeclarationsCountWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  config: Configuration
)(implicit mat: Materializer)
    extends WorkerConfig {

  private val logger = Logger(this.getClass)

  private val initialDelay: FiniteDuration =
    durationValueFromConfig("workers.declarations-count-worker.initial-delay", config)
  private val interval: FiniteDuration     =
    durationValueFromConfig("workers.declarations-count-worker.interval", config)

  val tap: SinkQueueWithCancel[DeclarationsCount] = {

    logger.info("[DeclarationsCountWorker][tap] Declarations count worker started")

    Source
      .tick(initialDelay, interval, ())
      .mapAsync(1)(_ => declarationsRepository.declarationsCount)
      .map { count =>
        logger.info(
          s"[DeclarationsCountWorker][tap] Declarations in DB - total: ${count.totalCount}, " +
            s"paid declarations not sent to ETMP: ${count.paidDeclarationsNotSentToEtmpCount}, " +
            s"paid amendments not sent to ETMP: ${count.paidAmendmentsNotSentToEtmpCount}"
        )
        count
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
}
