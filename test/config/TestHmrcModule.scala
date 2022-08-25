/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import akka.pattern.CircuitBreaker
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository, DefaultLockRepository, LockRepository}
import services.{ChargeReferenceService, SequentialChargeReferenceService}

class TestHmrcModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[DeclarationsRepository].to[DefaultDeclarationsRepository].eagerly,
      bind[ChargeReferenceService].to[SequentialChargeReferenceService].eagerly,
      bind[LockRepository].to[DefaultLockRepository].eagerly,
      bind[CircuitBreaker].qualifiedWith("des").toProvider[DesCircuitBreakerProvider]
    )
}
