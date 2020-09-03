/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package services

import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting

class ValidationServiceSpec extends FreeSpec with MustMatchers with OneAppPerSuite with Injecting {

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
