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
import org.apache.pekko.stream.Supervision
import play.api.Configuration
import play.api.i18n.Lang.logger
import repositories.LockRepository

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait BaseDeclarationWorker extends WorkerConfig {
  protected def lockRepository: LockRepository

  protected def getLock(declaration: Declaration)(implicit ec: ExecutionContext): Future[(Boolean, Declaration)] =
    lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)

  protected def lockSuccessful(data: (Boolean, Declaration)): List[Declaration]                                  =
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

  val supervisionStrategy: Supervision.Decider = {
    case NonFatal(e) =>
      logger.warn(s"[${this.getClass.getName}][supervisionStrategy] NonFatal exception returned, $e")
      Supervision.resume
    case e           =>
      logger.error(s"[${this.getClass.getName}][supervisionStrategy] Fatal exception returned, $e")
      Supervision.stop
  }

  def durationValueFromConfig(value: String, config: Configuration): FiniteDuration = {
    val valueFromConfigFiniteDuration = config.get[FiniteDuration](value)
    Try {
      val valueFromConfig = config.get[String](value).replace('.', ' ')
      Duration(valueFromConfig).asInstanceOf[FiniteDuration]
    }.getOrElse(valueFromConfigFiniteDuration)
  }
}
