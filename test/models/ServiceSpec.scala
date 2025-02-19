/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class ServiceSpec extends AnyWordSpec with Matchers {

  "Service" when {
    "be read from config" in {

      val config = Configuration(
        "service.host"     -> "localhost",
        "service.port"     -> "443",
        "service.protocol" -> "https"
      )

      val service = config.get[Service]("service")

      service shouldBe Service(
        host = "localhost",
        port = "443",
        protocol = "https"
      )
    }

    "return its base url" in {

      val service = Service(
        host = "localhost",
        port = "443",
        protocol = "https"
      )

      service.baseUrl shouldBe "https://localhost:443"
    }

    "return its base url through implicit conversion to a string" in {

      val service: String = Service(
        host = "localhost",
        port = "443",
        protocol = "https"
      )

      service shouldBe "https://localhost:443"
    }
  }
}
