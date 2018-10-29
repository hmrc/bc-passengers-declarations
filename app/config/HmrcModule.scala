package config

import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository}
import services.{ChargeReferenceService, SequentialChargeReferenceService}

class HmrcModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {

    Seq(
      bind[DeclarationsRepository].to[DefaultDeclarationsRepository],
      bind[ChargeReferenceService].to[SequentialChargeReferenceService]
    )
  }
}
