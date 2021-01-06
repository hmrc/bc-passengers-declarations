/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package services

import java.io.IOException

import com.fasterxml.jackson.core.JsonParseException
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting

class ResourceServiceSpec extends FreeSpec with MustMatchers with OneAppPerSuite with Injecting {

  private lazy val resourceService: ResourceService = inject[ResourceService]

  private val json = Json.obj(
    "foo" -> "bar"
  )

  "a resource service" - {

    "must return the contents of a file as a string" in {
      resourceService.getFile("test.txt") mustEqual "foo = bar"
    }

    "must return the contents of a file as json" in {
      resourceService.getJson("test.json") mustEqual json
    }

    "must throw an exception when a file can't be found" in {

      an[IOException] mustBe thrownBy {
        resourceService.getFile("non-existant.txt")
      }
    }

    "must throw an exception when a file can't be parsed as json" in {

      a[JsonParseException] mustBe thrownBy {
        resourceService.getJson("test.txt")
      }
    }
  }
}
