/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package config

import akka.pattern.CircuitBreaker
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository, DefaultLockRepository, LockRepository}
import services.{ChargeReferenceService, SequentialChargeReferenceService}

class TestHmrcModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {

    Seq(
      bind[DeclarationsRepository].to[DefaultDeclarationsRepository].eagerly,
      bind[ChargeReferenceService].to[SequentialChargeReferenceService].eagerly,
      bind[LockRepository].to[DefaultLockRepository].eagerly,
      bind[CircuitBreaker].qualifiedWith("des").toProvider[DesCircuitBreakerProvider]
    )
  }
}
