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

package workers

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import helpers.Constants
import models.declarations.Declaration
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.{Materializer, Supervision}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.slf4j.LoggerFactory
import play.api.{Configuration, Logger}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultLockRepository, LockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

class BaseDeclarationWorkerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with Constants
    with Eventually {
  private val mockLockRepository: DefaultLockRepository = Mockito.mock(classOf[DefaultLockRepository])
  private val mockConfiguration: Configuration          = Mockito.mock(classOf[Configuration])

  object BaseDeclarationWorkerTest extends BaseDeclarationWorker {
    override protected def lockRepository: LockRepository = mockLockRepository

    def testGetLock(declaration: Declaration): Future[(Boolean, Declaration)] =
      getLock(declaration)

    def testLockSuccessful(data: (Boolean, Declaration)): List[Declaration] =
      lockSuccessful(data)
  }

  private def withCapturedLogs(test: ListAppender[ILoggingEvent] => Unit): Unit =
    withCapturedLogsFor(BaseDeclarationWorkerTest)(test)

  private def withCapturedLogsFor(worker: AnyRef)(test: ListAppender[ILoggingEvent] => Unit): Unit = {
    val loggerName = Logger(worker.getClass).logger.getName
    val logger     = LoggerFactory.getLogger(loggerName).asInstanceOf[LogbackLogger]
    val appender   = new ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)
    try test(appender)
    finally logger.detachAppender(appender)
  }

  private def logOf(appender: ListAppender[ILoggingEvent]): Option[ILoggingEvent] =
    appender.list.asScala.find(_.getFormattedMessage.contains("[logIfStopped]"))

  object RunOnceWorkerTest extends WorkerConfig {
    override protected def runsOnce: Boolean = true
  }

  "BaseDeclarationWorkerSpec" when {
    "getLock" must {
      "return a tuple when the lock is successful" in {
        when(mockLockRepository.lock(any[Int]())).thenReturn(Future.successful(true))
        await(BaseDeclarationWorkerTest.testGetLock(declaration)) shouldBe (true, declaration)
      }
    }

    "lockSuccessful" must {
      "return the declaration in a list" in {
        BaseDeclarationWorkerTest.testLockSuccessful((true, declaration)) shouldBe List(declaration)
      }
      "return Nil when the lockStatus is false" in {
        BaseDeclarationWorkerTest.testLockSuccessful((false, declaration)) shouldBe Nil
      }
    }

    "supervisionStrategy" must {
      "set supervisionStrategy state to resume a worker queue when encountering a NonFatal Exception" in {

        val nonFatalException: Exception = new Exception("")

        BaseDeclarationWorkerTest.supervisionStrategy(nonFatalException) shouldBe Supervision.resume
      }

      "set supervisionStrategy state to stop a worker queue when encountering a Fatal Exception" in {

        val fatalException: VirtualMachineError = new VirtualMachineError {}

        BaseDeclarationWorkerTest.supervisionStrategy(fatalException) shouldBe Supervision.stop
      }
    }

    "logIfStopped" must {
      implicit val mat: Materializer = app.materializer

      "log an error with the exception name when the worker stream fails" in withCapturedLogs { appender =>
        val exception = new IllegalStateException("broken declaration")

        Source.failed[Int](exception).via(BaseDeclarationWorkerTest.logIfStopped).runWith(Sink.ignore)

        eventually {
          val log = logOf(appender).get
          log.getLevel                     shouldBe Level.ERROR
          log.getFormattedMessage            should include("will not run again until restart: IllegalStateException")
          log.getThrowableProxy.getMessage shouldBe "broken declaration"
        }
      }

      "log a warning when the worker stream completes" in withCapturedLogs { appender =>
        Source.single(1).via(BaseDeclarationWorkerTest.logIfStopped).runWith(Sink.ignore)

        eventually {
          val log = logOf(appender).get
          log.getLevel          shouldBe Level.WARN
          log.getFormattedMessage should include("Worker stream completed unexpectedly")
        }
      }

      "log info, not a warning, when a run-once worker stream completes" in withCapturedLogsFor(RunOnceWorkerTest) {
        appender =>
          Source.single(1).via(RunOnceWorkerTest.logIfStopped).runWith(Sink.ignore)

          eventually {
            val log = logOf(appender).get
            log.getLevel          shouldBe Level.INFO
            log.getFormattedMessage should include("Worker stream finished processing")
          }
      }

      "log info, not an error, when the worker stream stops because the application is shutting down" in
        withCapturedLogs { appender =>
          val system: ActorSystem                = ActorSystem("logIfStopped-shutdown-test")
          val shutdownMaterializer: Materializer = Materializer(system)

          Source
            .maybe[Int]
            .via(BaseDeclarationWorkerTest.logIfStopped(shutdownMaterializer))
            .runWith(Sink.ignore)(shutdownMaterializer)
          shutdownMaterializer.shutdown()

          try
            eventually {
              val log = logOf(appender).get
              log.getLevel          shouldBe Level.INFO
              log.getFormattedMessage should include("Worker stream stopped due to application shutdown")
            }
          finally await(system.terminate())
        }
    }

    "durationValueFromConfig" must {
      "return a configuration value as a FiniteDuration" in {
        when(mockConfiguration.get[FiniteDuration]("test")).thenReturn(2.seconds)

        BaseDeclarationWorkerTest.durationValueFromConfig("test", mockConfiguration) shouldBe 2.seconds
      }
      "return a configuration value as a FiniteDuration if it is a String" in {
        when(mockConfiguration.get[String]("test")).thenReturn("3.seconds")

        BaseDeclarationWorkerTest.durationValueFromConfig("test", mockConfiguration) shouldBe 3.seconds
      }
    }
  }
}
