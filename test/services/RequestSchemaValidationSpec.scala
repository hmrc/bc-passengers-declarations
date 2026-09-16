/*
 * Copyright 2026 HM Revenue & Customs
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

import helpers.Constants
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting

class RequestSchemaValidationSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with Injecting
    with Constants {

  private lazy val validationService: ValidationService = inject[ValidationService]
  private lazy val validator: Validator                 = validationService.get("declarationsRequestSchema.json")

  "the request schema" should {

    "accept a declaration with no declarationVaping section" in {
      validator.validate(declarationData) shouldBe empty
    }

    "accept a declaration with a declarationVaping section" in {
      validator.validate(declarationDataWithVaping) shouldBe empty
    }

    "reject a declarationVaping section with an unknown field" in {
      val invalid = declarationDataWithVaping.deepMerge(
        Json.obj(
          "simpleDeclarationRequest" -> Json.obj(
            "requestDetail" -> Json.obj(
              "declarationVaping" -> Json.obj("notARealField" -> "oops")
            )
          )
        )
      )

      validator.validate(invalid) should not be empty
    }

    "reject a declarationVaping item with a non-decimal-string goodsValue" in {
      val invalid = declarationDataWithVaping.deepMerge(
        Json.obj(
          "simpleDeclarationRequest" -> Json.obj(
            "requestDetail" -> Json.obj(
              "declarationVaping" -> Json.obj(
                "declarationItemVaping" -> Json.arr(Json.obj("goodsValue" -> "not-a-number"))
              )
            )
          )
        )
      )

      validator.validate(invalid) should not be empty
    }
  }
}
