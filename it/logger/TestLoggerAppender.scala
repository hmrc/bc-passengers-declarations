/*
 * Copyright 2024 HM Revenue & Customs
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
