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

package workers

import metrics.MetricsOperator
import models.DeclarationsStatus
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.DefaultDeclarationsRepository

class MetricsWorkerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = Mockito.mock(classOf[DefaultDeclarationsRepository])

  val metricsOperator: MetricsOperator = app.injector.instanceOf[MetricsOperator]
  val config: Configuration            = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val metricsWorker: MetricsWorker = new MetricsWorker(
      declarationsRepository = mockDeclarationsRepository,
      config = config,
      metricsOperator = metricsOperator
    )
  }

  "MetricsWorker" when {
    ".tap" must {
      "set the totals for metrics from a queue of DeclarationStatus and returns the current totals as a DeclarationStatus" in new Setup {

        val declarationStatus: DeclarationsStatus            = DeclarationsStatus(1, 2, 3, 1, 1)
        val alternativeDeclarationStatus: DeclarationsStatus = DeclarationsStatus(0, 2, 1, 3, 1)

        when(mockDeclarationsRepository.metricsCount).thenReturn(
          Source(Vector(declarationStatus, alternativeDeclarationStatus))
        )

        await(metricsWorker.tap.pull()) mustBe Some(declarationStatus)
        await(metricsWorker.tap.pull()) mustBe Some(alternativeDeclarationStatus)
      }
    }
  }

}
