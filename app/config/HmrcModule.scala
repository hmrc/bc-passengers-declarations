/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package config

import akka.pattern.CircuitBreaker
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository, DefaultLockRepository, LockRepository}
import services.{ChargeReferenceService, SequentialChargeReferenceService}
import workers.{DeclarationSubmissionWorker, FailedSubmissionWorker, MetricsWorker, PaymentTimeoutWorker}

class HmrcModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {

    Seq(
      bind[DeclarationsRepository].to[DefaultDeclarationsRepository].eagerly,
      bind[ChargeReferenceService].to[SequentialChargeReferenceService].eagerly,
      bind[LockRepository].to[DefaultLockRepository].eagerly,
      bind[CircuitBreaker].qualifiedWith("des").toProvider[DesCircuitBreakerProvider],
      bind[DeclarationSubmissionWorker].toSelf.eagerly,
      bind[PaymentTimeoutWorker].toSelf.eagerly,
      bind[FailedSubmissionWorker].toSelf.eagerly,
      bind[MetricsWorker].toSelf.eagerly
    )
  }
}
