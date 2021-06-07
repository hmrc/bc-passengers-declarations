/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package connectors

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

trait HttpDate {

  protected val dateFormatter: DateTimeFormatter =
    DateTimeFormat
      .forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
      .withZone(DateTimeZone.UTC)

  def now: String =
    dateFormatter.print(DateTime.now.withZone(DateTimeZone.UTC))
}
