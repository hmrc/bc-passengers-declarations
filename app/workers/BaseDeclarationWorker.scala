package workers

import models.declarations.Declaration
import repositories.LockRepository

import scala.concurrent.{ExecutionContext, Future}

trait BaseDeclarationWorker {

  protected def lockRepository: LockRepository

  protected def getLock(declaration: Declaration)(implicit ec: ExecutionContext): Future[(Boolean, Declaration)] =
    lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)

  protected def lockSuccessful(data: (Boolean, Declaration)): List[Declaration] =
    data match {
      case (hasLock, declaration) =>
        if (hasLock) List(declaration) else Nil
    }
}
