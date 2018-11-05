package workers

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import com.google.inject.Inject
import connectors.HODConnector
import models.SubmissionResponse
import models.declarations.{Declaration, State}
import play.api.Configuration
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

class DeclarationSubmissionWorker @Inject() (
                                              declarationsRepository: DeclarationsRepository,
                                              lockRepository: LockRepository,
                                              hodConnector: HODConnector,
                                              config: Configuration
                                            )(implicit mat: Materializer, ec: ExecutionContext) {

  private val initialDelay = config.get[FiniteDuration]("workers.declaration-submission-worker.initial-delay")
  private val interval = config.get[FiniteDuration]("workers.declaration-submission-worker.interval")
  private val parallelism = config.get[Int]("workers.declaration-submission-worker.parallelism")
  private val elements = config.get[Int]("workers.declaration-submission-worker.throttle.elements")
  private val per = config.get[FiniteDuration]("workers.declaration-submission-worker.throttle.per")

  private val supervisionStrategy: Supervision.Decider = {
    case NonFatal(_) => Supervision.resume
    case _           => Supervision.stop
  }

  val tap: SinkQueueWithCancel[(Declaration, SubmissionResponse)] =
    Source.tick(initialDelay, interval, declarationsRepository.paidDeclarations)
      .flatMapConcat(identity)
      .throttle(elements, per)
      .mapAsync(parallelism)(getLock)
      .mapConcat(lockSuccessful)
      .mapAsync(parallelism) {
        declaration =>
          for {
            result <- hodConnector.submit(declaration)
            _      <- result match {
              case SubmissionResponse.Submitted =>
                declarationsRepository.remove(declaration.chargeReference)
              case SubmissionResponse.Error =>
                Future.successful(())
              case SubmissionResponse.Failed =>
                declarationsRepository.setState(declaration.chargeReference, State.Failed)
            }
          } yield (declaration, result)
      }.wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
        .run()

  private def getLock(declaration: Declaration): Future[(Boolean, Declaration)] =
    lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)

  private def lockSuccessful(data: (Boolean, Declaration)): List[Declaration] =
    data match {
      case (hasLock, declaration) =>
        if (hasLock) List(declaration) else Nil
    }
}
