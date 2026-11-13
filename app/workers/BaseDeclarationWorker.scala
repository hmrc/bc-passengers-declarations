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

import models.declarations.Declaration
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.{AbruptStageTerminationException, AbruptTerminationException, Materializer, Supervision}
import play.api.{Configuration, Logger}
import repositories.LockRepository

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait BaseDeclarationWorker extends WorkerConfig {
  protected def lockRepository: LockRepository

  protected def getLock(declaration: Declaration)(implicit ec: ExecutionContext): Future[(Boolean, Declaration)] =
    lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)

  protected def lockSuccessful(data: (Boolean, Declaration)): List[Declaration] =
    data match {
      case (hasLock, declaration) =>
        if (hasLock) {
          List(declaration)
        } else {
          Nil
        }
    }
}

trait WorkerConfig {

  private lazy val workerLogger: Logger = Logger(this.getClass)

  // Workers that process their records once at startup (no source tick) are expected to complete.
  protected def runsOnce: Boolean = false

  val supervisionStrategy: Supervision.Decider = {
    case NonFatal(e) =>
      workerLogger.warn("NonFatal exception returned", e)
      Supervision.resume
    case e           =>
      workerLogger.error("Fatal exception returned", e)
      Supervision.stop
  }

  def logIfStopped[T](implicit mat: Materializer): Flow[T, T, NotUsed] =
    Flow[T].watchTermination() { (_, done) =>
      val name = this.getClass.getSimpleName
      done.onComplete {
        case Failure(_: AbruptStageTerminationException | _: AbruptTerminationException) =>
          workerLogger.info("Worker stream stopped due to application shutdown")
        case Failure(e)                                                                  =>
          workerLogger.error(
            "Worker stream stopped with an error and will not run again until restart: " +
              e.getClass.getSimpleName,
            e
          )
        case Success(_) if runsOnce                                                      =>
          workerLogger.info("Worker stream finished processing")
        case Success(_)                                                                  =>
          workerLogger.warn(
            "Worker stream completed unexpectedly and will not run again until restart"
          )
      }(mat.executionContext)
      NotUsed
    }

  def durationValueFromConfig(value: String, config: Configuration): FiniteDuration = {
    val valueFromConfigFiniteDuration = config.get[FiniteDuration](value)
    Try {
      val valueFromConfig = config.get[String](value).replace('.', ' ')
      Duration(valueFromConfig).asInstanceOf[FiniteDuration]
    }.getOrElse(valueFromConfigFiniteDuration)
  }
}
