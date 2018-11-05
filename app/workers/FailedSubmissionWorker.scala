package workers

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import javax.inject.Inject
import models.declarations.{Declaration, State}
import play.api.Configuration
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class FailedSubmissionWorker @Inject()(
                                        declarationsRepository: DeclarationsRepository,
                                        override protected val lockRepository: LockRepository,
                                        config: Configuration
                                      )(implicit ec: ExecutionContext, mat: Materializer)
  extends BaseDeclarationWorker {

    private val parallelism = config.get[Int]("workers.failed-submission-worker.parallelism")

    private val decider: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[Declaration] =
      declarationsRepository.failedDeclarations
        .mapAsync(parallelism)(getLock)
        .mapConcat(lockSuccessful)
        .mapAsync(parallelism) {
          declaration =>

            declarationsRepository.setState(declaration.chargeReference, State.Paid)
        }.wireTapMat(Sink.queue())(Keep.right)
          .toMat(Sink.ignore)(Keep.left)
          .withAttributes(ActorAttributes.supervisionStrategy(decider))
          .run()
}
