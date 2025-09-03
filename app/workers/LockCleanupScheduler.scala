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

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import scala.concurrent.duration._
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import repositories.LockRepository

import play.api.Logger

@Singleton
class LockCleanupScheduler @Inject()(
                                      lockRepository: LockRepository,
                                      actorSystem: ActorSystem
                                    )(implicit ec: ExecutionContext) {

  private val ttlSeconds: Long = 300
  private var cancellable: Option[Cancellable] = None

  private val logger = Logger(this.getClass)

  private def cleanup(): Unit = {
    lockRepository.removeLegacyLocksOlderThanTtl(ttlSeconds).onComplete {
      case Success(deletedCount) =>
        logger.info(s"[LockCleanupScheduler][cancellable] LockCleanupScheduler: Removed $deletedCount legacy locks older than TTL.")
      case Failure(ex) =>
        logger.info(s"[LockCleanupScheduler][cancellable] LockCleanupScheduler: Error during cleanup: ${ex.getMessage}")
    }
  }

  cancellable = Some(actorSystem.scheduler.scheduleOnce(
    6.minutes
  ) { cleanup() })
}