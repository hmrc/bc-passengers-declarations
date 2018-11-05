package config

import akka.actor.ActorSystem
import akka.pattern.CircuitBreaker
import com.google.inject.{Inject, Provider, Singleton}
import play.api.Configuration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

@Singleton
class DesCircuitBreakerProvider @Inject()(config: Configuration)
                                         (implicit ec: ExecutionContext, sys: ActorSystem) extends Provider[CircuitBreaker] {

  private val maxFailures  = config.get[Int]("microservice.services.des.circuit-breaker.max-failures")
  private val callTimeout  = config.get[FiniteDuration]("microservice.services.des.circuit-breaker.call-timeout")
  private val resetTimeout = config.get[FiniteDuration]("microservice.services.des.circuit-breaker.reset-timeout")

  override def get(): CircuitBreaker =
    new CircuitBreaker(
      scheduler    = sys.scheduler,
      maxFailures  = maxFailures,
      callTimeout  = callTimeout,
      resetTimeout = resetTimeout
    )
}
