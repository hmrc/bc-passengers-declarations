package connectors

import org.joda.time.{DateTimeZone, LocalDateTime}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

trait HttpDate {

  protected val dateFormatter: DateTimeFormatter =
    DateTimeFormat
      .forPattern("EEE, dd MMM yyyy HH:mm:ss z")
      .withZone(DateTimeZone.UTC)

  def now: String =
    dateFormatter.print(LocalDateTime.now)
}
