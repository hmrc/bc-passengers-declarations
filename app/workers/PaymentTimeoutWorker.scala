package workers

import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import javax.inject.Inject
import models.Declaration
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

class PaymentTimeoutWorker @Inject()(
                                     declarationsRepository: DeclarationsRepository,
                                     lockRepository: LockRepository,
                                     config: Configuration
                                    )(implicit ec: ExecutionContext, mat: Materializer) {

  private val logger: Logger = Logger(this.getClass)

  private val initialDelay = config.get[FiniteDuration]("workers.payment-timeout-worker.initial-delay")
  private val interval = config.get[FiniteDuration]("workers.payment-timeout-worker.interval")
  private val parallelism = config.get[Int]("workers.payment-timeout-worker.parallelism")

  private val decider: Supervision.Decider = {
    case NonFatal(_) => Supervision.resume
    case _           => Supervision.stop
  }

  val tap: SinkQueueWithCancel[Declaration] =
    Source.tick(initialDelay, interval, declarationsRepository.staleDeclarations)
      .flatMapConcat(identity)
      .mapAsync(parallelism)(getLock)
      .filter(lockSuccessful)
      .map {
        case (_, declaration) =>

          logger.info(s"Declaration ${declaration.chargeReference.value} is stale")

          declaration
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
