package workers

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel}
import javax.inject.Inject
import models.declarations.{Declaration, State}
import play.api.Configuration
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class FailedSubmissionWorker @Inject()(
                                        declarationsRepository: DeclarationsRepository,
                                        lockRepository: LockRepository,
                                        config: Configuration
                                      )(implicit ec: ExecutionContext, mat: Materializer) {

  private val parallelism = config.get[Int]("workers.failed-submission-worker.parallelism")

  private val decider: Supervision.Decider = {
    case NonFatal(_) => Supervision.resume
    case _           => Supervision.stop
  }

  val tap: SinkQueueWithCancel[Declaration] =
    declarationsRepository.failedDeclarations
      .mapAsync(parallelism)(getLock)
      .filter(lockSuccessful)
      .mapAsync(parallelism) {
        case (_, declaration) =>

          declarationsRepository.setState(declaration.chargeReference, State.Paid)
      }.wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
        .run()

  private def getLock(declaration: Declaration): Future[(Boolean, Declaration)] =
    lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)

  private def lockSuccessful(data: (Boolean, Declaration)): Boolean =
    data match {
      case (hasLock, _) =>
        hasLock
    }
}
