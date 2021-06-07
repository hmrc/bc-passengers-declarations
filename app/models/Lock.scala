/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

import java.time.{LocalDateTime, ZoneOffset}
import play.api.libs.json.{Json, OFormat}
import repositories.MongoDateTimeFormats

final case class Lock(_id: Int, lastUpdated: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC))

object Lock extends MongoDateTimeFormats {

  implicit val formats: OFormat[Lock] = Json.format
}
