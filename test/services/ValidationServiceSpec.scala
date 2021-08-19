/*
 * Copyright 2021 HM Revenue & Customs
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

package services

import play.api.libs.json.Json
import play.api.test.Injecting
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ValidationServiceSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with Injecting {

  private lazy val validationService: ValidationService = inject[ValidationService]
  private lazy val validator: Validator = validationService.get("test-schema.json")

  "a validator" - {

    "must return an empty list of errors when a document is valid" in {

      val json = Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "foo" -> "bar"
        )
      )

      validator.validate(json) mustBe empty
    }

    "must return a list of validation errors when a document is invalid" in {

      val json = Json.obj()

      validator.validate(json) must contain (
        """object has missing required properties (["simpleDeclarationRequest"])"""
      )
    }
  }
}
