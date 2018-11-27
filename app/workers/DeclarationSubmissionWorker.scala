package workers

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import com.google.inject.{Inject, Singleton}
import connectors.HODConnector
import models.SubmissionResponse
import models.declarations.{Declaration, State}
import org.joda.time.DateTime
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class DeclarationSubmissionWorker @Inject() (
  declarationsRepository: DeclarationsRepository,
  override protected val lockRepository: LockRepository,
  hodConnector: HODConnector,
  config: Configuration
)(implicit mat: Materializer, ec: ExecutionContext)
  extends BaseDeclarationWorker {

    private val logger = Logger(this.getClass)

    private val initialDelay = config.get[FiniteDuration]("workers.declaration-submission-worker.initial-delay")
    private val interval = config.get[FiniteDuration]("workers.declaration-submission-worker.interval")
    private val parallelism = config.get[Int]("workers.declaration-submission-worker.parallelism")
    private val elements = config.get[Int]("workers.declaration-submission-worker.throttle.elements")
    private val per = config.get[FiniteDuration]("workers.declaration-submission-worker.throttle.per")

    private val supervisionStrategy: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[(Declaration, SubmissionResponse)] = {

      logger.info("Declaration submission worker started")

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
                  declarationsRepository.setState(declaration.chargeReference, State.SubmissionFailed)
              }
            } yield (declaration, result)
        }.wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
        .run()
    }
}
