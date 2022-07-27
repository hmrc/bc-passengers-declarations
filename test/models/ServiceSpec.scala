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

package models


import org.scalatest.matchers.must.Matchers
import org.scalatest.freespec.AnyFreeSpec
import play.api.Configuration

class ServiceSpec extends AnyFreeSpec with Matchers {

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
