package workers

import akka.stream.scaladsl.{Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorAttributes, Materializer, Supervision}
import javax.inject.{Inject, Singleton}
import models.declarations.Declaration
import play.api.{Configuration, Logger}
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

@Singleton
class PaymentTimeoutWorker @Inject()(
                                     declarationsRepository: DeclarationsRepository,
                                     override protected val lockRepository: LockRepository,
                                     config: Configuration
                                    )(implicit ec: ExecutionContext, mat: Materializer)
  extends BaseDeclarationWorker {

    private val logger: Logger = Logger(this.getClass)

    private val initialDelay = config.get[FiniteDuration]("workers.payment-timeout-worker.initial-delay")
    private val interval = config.get[FiniteDuration]("workers.payment-timeout-worker.interval")
    private val parallelism = config.get[Int]("workers.payment-timeout-worker.parallelism")

    private val supervisionStrategy: Supervision.Decider = {
      case NonFatal(_) => Supervision.resume
      case _           => Supervision.stop
    }

    val tap: SinkQueueWithCancel[Declaration] = {

      logger.info("Payment timeout worker started")

      Source.tick(initialDelay, interval, declarationsRepository.staleDeclarations)
        .flatMapConcat(identity)
        .mapAsync(parallelism)(getLock)
        .mapConcat(lockSuccessful)
        .map {
          declaration =>

            logger.info(s"Declaration ${declaration.chargeReference.value} is stale")

            declaration
        }.wireTapMat(Sink.queue())(Keep.right)
        .toMat(Sink.ignore)(Keep.left)
        .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
        .run()
    }
}
