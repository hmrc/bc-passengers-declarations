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
import models.declarations.Declaration
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.libs.json.JsObject
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class DeclarationDeletionWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  config: Configuration
)(implicit mat: Materializer, ec: ExecutionContext)
    extends BaseDeclarationWorker {

  private val logger = Logger(this.getClass)

  private val initialDelayFromConfig               =
    config.get[String]("workers.declaration-deletion-worker.initial-delay").replace('.', ' ')
  private val initialDelayFromConfigFiniteDuration =
    config.get[FiniteDuration]("workers.declaration-deletion-worker.initial-delay")
  private val finiteInitialDelay                   = Duration(initialDelayFromConfig)
  private val initialDelay                         =
    Some(finiteInitialDelay).collect { case d: FiniteDuration => d }.getOrElse(initialDelayFromConfigFiniteDuration)

  private val intervalFromConfig                   = config.get[String]("workers.declaration-deletion-worker.interval").replace('.', ' ')
  private val intervalFromConfigFiniteDuration     =
    config.get[FiniteDuration]("workers.declaration-deletion-worker.interval")
  private val finiteInterval                       = Duration(intervalFromConfig)
  private val interval                             =
    Some(finiteInterval).collect { case d: FiniteDuration => d }.getOrElse(intervalFromConfigFiniteDuration)

  private val parallelism                          = config.get[Int]("workers.declaration-deletion-worker.parallelism")

  private val timeToHoldFromConfig                     =
    config.get[String]("workers.declaration-deletion-worker.timeToHold").replace('.', ' ')
  private val timeToHoldFromConfigFiniteDuration       =
    config.get[FiniteDuration]("workers.declaration-deletion-worker.timeToHold")
  private val finiteTimeToHold                         = Duration(timeToHoldFromConfig)
  private val timeToHold                               =
    Some(finiteTimeToHold).collect { case d: FiniteDuration => d }.getOrElse(timeToHoldFromConfigFiniteDuration)

  private val supervisionStrategy: Supervision.Decider = {
    case NonFatal(_) => Supervision.resume
    case _           => Supervision.stop
  }

  val tap: SinkQueueWithCancel[Declaration] = {
    logger.info("Declaration deletion worker started")
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
      DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(DateTimeZone.UTC)
    val receiptDateTime                  = DateTime.parse(dateString, dateFormatter)
    receiptDateTime.plusMinutes(timeToHold.toMinutes.toInt).isBefore(DateTime.now.withZone(DateTimeZone.UTC))
  }
}
