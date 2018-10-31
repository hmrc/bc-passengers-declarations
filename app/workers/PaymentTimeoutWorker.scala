package workers

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import javax.inject.Inject
import models.Declaration
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class PaymentTimeoutWorker @Inject()(
                                     declarationsRepository: DeclarationsRepository,
                                     lockRepository: LockRepository,
                                     config: Configuration
                                    )(implicit ec: ExecutionContext, mat: Materializer) {

  private val logger: Logger = Logger(this.getClass)

  val initialDelay = config.get[FiniteDuration]("workers.payment-timeout-worker.initial-delay")
  val interval = config.get[FiniteDuration]("workers.payment-timeout-worker.interval")

  val tap: SinkQueueWithCancel[Declaration] =
    Source.tick(initialDelay, interval, declarationsRepository.staleDeclarations)
      .flatMapConcat(identity)
      .mapAsync(4) {
        declaration =>
          lockRepository.lock(declaration.chargeReference.value).map(_ -> declaration)
      }
      .filter {
        case (hasLock, _) =>
          hasLock
      }
      .map {
        case (_, declaration) =>

          logger.info(s"Declaration ${declaration.chargeReference.value} is stale")

          declaration
      }.wireTapMat(Sink.queue())(Keep.right)
      .toMat(Sink.ignore)(Keep.left)
      .run()
}
