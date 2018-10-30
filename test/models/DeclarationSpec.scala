package models

import java.time.LocalDateTime

import org.scalatest.{FreeSpec, MustMatchers}
import play.api.libs.json.Json

class DeclarationSpec extends FreeSpec with MustMatchers {

  "a Declaration" - {

    "must deserialise" in {

      val lastUpdated = LocalDateTime.now

      val json = Json.obj(
        "_id" -> "123",
        "data" -> Json.obj(),
        "lastUpdated" -> Json.toJson(lastUpdated)
      )

      json.as[Declaration] mustEqual
        Declaration(
          ChargeReference("123"),
          Json.obj(),
          lastUpdated
        )
    }

    "must serialise" in {

      val lastUpdated = LocalDateTime.now

      val json = Json.obj(
        "_id" -> "123",
        "data" -> Json.obj(),
        "lastUpdated" -> Json.toJson(lastUpdated)
      )

      Json.toJson(Declaration(ChargeReference("123"), Json.obj(), lastUpdated)) mustEqual json
    }
  }
}