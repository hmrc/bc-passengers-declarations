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

import models.declarations.Declaration
import org.apache.pekko.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import org.apache.pekko.stream.{ActorAttributes, Materializer}
import play.api.libs.json.JsObject
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class DeclarationDeletionWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit mat: Materializer, ec: ExecutionContext)
    extends BaseDeclarationWorker {

  private val logger = Logger(this.getClass)

  private val initialDelay: FiniteDuration =
    durationValueFromConfig("workers.declaration-deletion-worker.initial-delay", config)
  private val interval: FiniteDuration     =
    durationValueFromConfig("workers.declaration-deletion-worker.interval", config)
  private val timeToHold: FiniteDuration   =
    durationValueFromConfig("workers.declaration-deletion-worker.timeToHold", config)
  private val parallelism: Int             = config.get[Int]("workers.declaration-deletion-worker.parallelism")

  val tap: SinkQueueWithCancel[Declaration]                   = {
    logger.info("[DeclarationDeletionWorker][tap] Declaration deletion worker started")
    Source
      .tick(initialDelay, interval, declarationsRepository.paidDeclarationsForDeletion)
      .flatMapConcat(identity)
      .collect {
        case declaration if checkDeleteCondition(declaration) => declaration
      }
      .mapAsync(parallelism)(getLock)
      .mapConcat(lockSuccessful)
      .map { declaration =>
        logger.info(
          s"[DeclarationDeletionWorker][tap] Declaration ${declaration.chargeReference.value} is Paid, sent to ETMP, and crossed the time to hold, hence deleting"
        )
        declarationsRepository.remove(declaration.chargeReference)
        declaration
      }
      .wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
      .run()
  }
  def checkDeleteCondition(declaration: Declaration): Boolean = {
    val dateString                       = declaration.data
      .apply("simpleDeclarationRequest")
      .as[JsObject]
      .apply("requestCommon")
      .as[JsObject]
      .apply("receiptDate")
      .as[String]
    val dateFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    val receiptDateTime                  = ZonedDateTime.parse(dateString, dateFormatter)
    receiptDateTime.plusMinutes(timeToHold.toMinutes.toInt).isBefore(ZonedDateTime.now(ZoneOffset.UTC))
  }
}
