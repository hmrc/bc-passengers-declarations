package logger

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import scala.collection.mutable

class TestLoggerAppender extends AppenderBase[ILoggingEvent] {

  def append(eventObject: ILoggingEvent): Unit =
    TestLoggerAppender.queue.enqueue(eventObject)
}

object TestLoggerAppender {

  val queue: mutable.Queue[ILoggingEvent] = mutable.Queue.empty
}
