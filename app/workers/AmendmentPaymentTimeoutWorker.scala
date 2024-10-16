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

import models.declarations.Declaration
import org.apache.pekko.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import org.apache.pekko.stream.{ActorAttributes, Materializer}
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class AmendmentPaymentTimeoutWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit ec: ExecutionContext, mat: Materializer)
    extends BaseDeclarationWorker {

  private val logger: Logger = Logger(this.getClass)

  private val initialDelay: FiniteDuration   =
    durationValueFromConfig("workers.amendment-payment-timeout-worker.initial-delay", config)
  private val interval: FiniteDuration       =
    durationValueFromConfig("workers.amendment-payment-timeout-worker.interval", config)
  private val paymentTimeout: FiniteDuration =
    durationValueFromConfig("declarations.payment-no-response-timeout", config)
  private val parallelism: Int               = config.get[Int]("workers.amendment-payment-timeout-worker.parallelism")

  val tap: SinkQueueWithCancel[Declaration] = {

    logger.info("[AmendmentPaymentTimeoutWorker][tap] Amendment payment timeout worker started")

    def timeout: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).minus(paymentTimeout.toMillis, ChronoUnit.MILLIS)

    Source
      .tick(initialDelay, interval, declarationsRepository.unpaidAmendments)
      .flatMapConcat(identity)
      .collect {
        case declaration if declaration.lastUpdated.isBefore(timeout) => declaration
      }
      .mapAsync(parallelism)(getLock)
      .mapConcat(lockSuccessful)
      .map { declaration =>
        logger.info(
          s"[AmendmentPaymentTimeoutWorker][tap] Declaration ${declaration.chargeReference.value} is stale, deleting"
        )

        declarationsRepository.remove(declaration.chargeReference)

        declaration
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
}
