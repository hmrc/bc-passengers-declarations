package models

import java.time.LocalDateTime

import play.api.libs.json.{Json, OFormat}

final case class Lock(_id: String, lastUpdated: LocalDateTime = LocalDateTime.now)

object Lock {

  implicit val formats: OFormat[Lock] = Json.format
}
