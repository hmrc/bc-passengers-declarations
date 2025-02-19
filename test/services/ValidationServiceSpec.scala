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

package services

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting

class ValidationServiceSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with Injecting {

  private lazy val validationService: ValidationService = inject[ValidationService]
  private lazy val validator: Validator                 = validationService.get("test-schema.json")

  "Validator" when {
    "return an empty list of errors when a document is valid" in {

      val json = Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "foo" -> "bar"
        )
      )

      validator.validate(json) shouldBe empty
    }

    "return a list of validation errors when a document is invalid" in {

      val json = Json.obj()

      validator.validate(json) should contain(
        """object has missing required properties (["simpleDeclarationRequest"])"""
      )
    }
  }
}
