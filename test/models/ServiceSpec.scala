/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package models

import org.scalatest.{FreeSpec, MustMatchers}
import play.api.Configuration

class ServiceSpec extends FreeSpec with MustMatchers {

  "a service" - {

    "must be read from config" in {

      val config = Configuration(
        "service.host"     -> "localhost",
        "service.port"     -> "443",
        "service.protocol" -> "https"
      )

      val service = config.get[Service]("service")

      service mustEqual Service(
        host     = "localhost",
        port     = "443",
        protocol = "https"
      )
    }

    "must return its base url" in {

      val service = Service(
        host     = "localhost",
        port     = "443",
        protocol = "https"
      )

      service.baseUrl mustEqual "https://localhost:443"
    }

    "must return its base url through implicit conversion to string" in {

      val service: String = Service(
        host     = "localhost",
        port     = "443",
        protocol = "https"
      )

      service mustEqual "https://localhost:443"
    }
  }
}
