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

import models.declarations.Declaration
import repositories.LockRepository

import scala.concurrent.{ExecutionContext, Future}

trait BaseDeclarationWorker {

  protected def lockRepository: LockRepository

  protected def getLock(declaration: Declaration)(implicit ec: ExecutionContext): Future[(Boolean, Declaration)] =
    lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)

  protected def lockSuccessful(data: (Boolean, Declaration)): List[Declaration]                                  =
    data match {
      case (hasLock, declaration) =>
        if (hasLock) List(declaration) else Nil
    }
}
