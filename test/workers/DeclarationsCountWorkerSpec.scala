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

import models.DeclarationsCount
import org.apache.pekko.stream.Materializer
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.DefaultDeclarationsRepository

import scala.concurrent.Future

class DeclarationsCountWorkerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = Mockito.mock(classOf[DefaultDeclarationsRepository])

  val config: Configuration = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val declarationsCountWorker: DeclarationsCountWorker = new DeclarationsCountWorker(
      declarationsRepository = mockDeclarationsRepository,
      config = config
    )
  }

  "DeclarationsCountWorker" when {
    ".tap" must {
      "return the current declarations count from the repository" in new Setup {

        val declarationsCount: DeclarationsCount = DeclarationsCount(10, 3, 1)

        when(mockDeclarationsRepository.declarationsCount).thenReturn(Future.successful(declarationsCount))

        await(declarationsCountWorker.tap.pull()) shouldBe Some(declarationsCount)
      }
    }
  }

}
