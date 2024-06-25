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

import connectors.HODConnector
import helpers.Constants
import models.SubmissionResponse
import models.declarations.{Declaration, State}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mockito.Mockito
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditingTools

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AmendmentSubmissionWorkerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with Constants {

  val mockDeclarationsRepository: DefaultDeclarationsRepository = Mockito.mock(classOf[DefaultDeclarationsRepository])
  val mockLockRepository: DefaultLockRepository                 = Mockito.mock(classOf[DefaultLockRepository])
  val mockHodConnector: HODConnector                            = Mockito.mock(classOf[HODConnector])
  val mockAuditConnector: AuditConnector                        = Mockito.mock(classOf[AuditConnector])
  val mockAuditingTools: AuditingTools                          = Mockito.mock(classOf[AuditingTools])

  val config: Configuration = app.injector.instanceOf[Configuration]

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  trait Setup {
    lazy val amendmentSubmissionWorker = new AmendmentSubmissionWorker(
      mockDeclarationsRepository,
      lockRepository = mockLockRepository,
      hodConnector = mockHodConnector,
      config = config,
      auditConnector = mockAuditConnector,
      auditingTools = mockAuditingTools
    )
  }

  override def beforeEach(): Unit = {
    reset(mockDeclarationsRepository)
    reset(mockLockRepository)
  }

  "AmendmentSubmissionWorker" when {
    ".tap" must {
      "submit a queue of paid amendments to Etmp and returns amendments with a Submitted response" in new Setup {

        val queuedAmendment: Declaration = amendment.copy(
          randomChargeReference(),
          sentToEtmp = true,
          amendState = Some(State.Paid),
          amendSentToEtmp = Some(false)
        )

        when(mockDeclarationsRepository.paidAmendmentsForEtmp).thenReturn(Source(Vector(amendment, queuedAmendment)))

        when(mockLockRepository.lock(amendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(amendment, isAmendment = true))
          .thenReturn(Future.successful(SubmissionResponse.Submitted))
        when(mockDeclarationsRepository.setAmendSentToEtmp(amendment.chargeReference, amendSentToEtmp = true))
          .thenReturn(Future.successful(amendment))
        when(mockLockRepository.release(amendment.chargeReference.value)).thenReturn(Future.unit)

        await(amendmentSubmissionWorker.tap.pull()) mustBe Some((amendment, SubmissionResponse.Submitted))

        when(mockLockRepository.lock(queuedAmendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(queuedAmendment, isAmendment = true))
          .thenReturn(Future.successful(SubmissionResponse.Submitted))
        when(mockDeclarationsRepository.setAmendSentToEtmp(queuedAmendment.chargeReference, amendSentToEtmp = true))
          .thenReturn(Future.successful(queuedAmendment))
        when(mockLockRepository.release(queuedAmendment.chargeReference.value)).thenReturn(Future.unit)

        await(amendmentSubmissionWorker.tap.pull()) mustBe Some((queuedAmendment, SubmissionResponse.Submitted))

      }

      "return an amendment with an Error response when DES does not respond" in new Setup {

        when(mockDeclarationsRepository.paidAmendmentsForEtmp).thenReturn(Source(Vector(amendment)))

        when(mockLockRepository.lock(amendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(amendment, isAmendment = true))
          .thenReturn(Future.successful(SubmissionResponse.Error))
        when(mockLockRepository.release(amendment.chargeReference.value)).thenReturn(Future.unit)

        await(amendmentSubmissionWorker.tap.pull()) mustBe Some((amendment, SubmissionResponse.Error))

      }

      "return an amendment with a ParsingException response when there is a problem found in the amendment data" in new Setup {

        when(mockDeclarationsRepository.paidAmendmentsForEtmp).thenReturn(Source(Vector(amendment)))

        when(mockLockRepository.lock(amendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(amendment, isAmendment = true))
          .thenReturn(Future.successful(SubmissionResponse.ParsingException))
        when(mockLockRepository.release(amendment.chargeReference.value)).thenReturn(Future.unit)

        await(amendmentSubmissionWorker.tap.pull()) mustBe Some((amendment, SubmissionResponse.ParsingException))

      }

      "return an amendment with a Failed response when a Bad Request is received from DES" in new Setup {

        when(mockDeclarationsRepository.paidAmendmentsForEtmp).thenReturn(Source(Vector(amendment)))

        when(mockLockRepository.lock(amendment.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(amendment, isAmendment = true))
          .thenReturn(Future.successful(SubmissionResponse.Failed))
        when(mockDeclarationsRepository.setAmendState(amendment.chargeReference, State.SubmissionFailed))
          .thenReturn(Future.successful(amendment))
        when(mockLockRepository.release(amendment.chargeReference.value)).thenReturn(Future.unit)

        await(amendmentSubmissionWorker.tap.pull()) mustBe Some((amendment, SubmissionResponse.Failed))

      }
    }
  }
}
