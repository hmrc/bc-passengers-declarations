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
import scala.util.control.ControlThrowable

class DeclarationSubmissionWorkerSpec
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
    lazy val declarationSubmissionWorker = new DeclarationSubmissionWorker(
      declarationsRepository = mockDeclarationsRepository,
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

  "DeclarationSubmissionWorker" when {
    ".tap" must {
      "submit a queue of paid declarations to Etmp and returns a declaration with Submitted response" in new Setup {

        val queuedDeclaration: Declaration = declaration.copy(randomChargeReference())

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(
          Source(Vector(declaration, queuedDeclaration))
        )

        when(mockLockRepository.lock(declaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(declaration, isAmendment = false))
          .thenReturn(Future.successful(SubmissionResponse.Submitted))
        when(mockDeclarationsRepository.setSentToEtmp(declaration.chargeReference, sentToEtmp = true))
          .thenReturn(Future.successful(declaration))
        when(mockLockRepository.release(declaration.chargeReference.value)).thenReturn(Future.unit)

        await(declarationSubmissionWorker.tap.pull()) mustBe Some((declaration, SubmissionResponse.Submitted))

        when(mockLockRepository.lock(queuedDeclaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(queuedDeclaration, isAmendment = false))
          .thenReturn(Future.successful(SubmissionResponse.Submitted))
        when(mockDeclarationsRepository.setSentToEtmp(queuedDeclaration.chargeReference, sentToEtmp = true))
          .thenReturn(Future.successful(queuedDeclaration))
        when(mockLockRepository.release(queuedDeclaration.chargeReference.value)).thenReturn(Future.unit)

        await(declarationSubmissionWorker.tap.pull()) mustBe Some((queuedDeclaration, SubmissionResponse.Submitted))

      }

      "return a declaration with an Error response when DES does not respond" in new Setup {

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(Source(Vector(declaration)))

        when(mockLockRepository.lock(declaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(declaration, isAmendment = false))
          .thenReturn(Future.successful(SubmissionResponse.Error))
        when(mockLockRepository.release(declaration.chargeReference.value)).thenReturn(Future.unit)

        await(declarationSubmissionWorker.tap.pull()) mustBe Some((declaration, SubmissionResponse.Error))

      }

      "return a declaration with a ParsingException response when there is a problem found in the declaration data" in new Setup {

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(Source(Vector(declaration)))

        when(mockLockRepository.lock(declaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(declaration, isAmendment = false))
          .thenReturn(Future.successful(SubmissionResponse.ParsingException))
        when(mockLockRepository.release(declaration.chargeReference.value)).thenReturn(Future.unit)

        await(declarationSubmissionWorker.tap.pull()) mustBe Some((declaration, SubmissionResponse.ParsingException))

      }

      "return a declaration with a Failed response when a Bad Request is received from DES" in new Setup {

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(Source(Vector(declaration)))

        when(mockLockRepository.lock(declaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(declaration, isAmendment = false))
          .thenReturn(Future.successful(SubmissionResponse.Failed))
        when(mockDeclarationsRepository.setState(declaration.chargeReference, State.SubmissionFailed))
          .thenReturn(Future.successful(declaration))
        when(mockLockRepository.release(declaration.chargeReference.value)).thenReturn(Future.unit)

        await(declarationSubmissionWorker.tap.pull()) mustBe Some((declaration, SubmissionResponse.Failed))

      }

      "reach no result if a Declaration is not successfully Locked" in new Setup {

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(Source(Vector(declaration)))

        when(mockLockRepository.lock(declaration.chargeReference.value)).thenReturn(Future.successful(false))

        declarationSubmissionWorker.tap.pull().value mustBe None

      }

      "throw a NonFatal exception is thrown processing a declaration and resumes to the next available declaration" in new Setup {

        val queuedDeclaration: Declaration = declaration.copy(randomChargeReference())

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(
          Source(Vector(declaration, queuedDeclaration))
        )

        when(mockLockRepository.lock(declaration.chargeReference.value))
          .thenThrow(new RuntimeException("Test Exception"))

        when(mockLockRepository.lock(queuedDeclaration.chargeReference.value)).thenReturn(Future.successful(true))
        when(mockHodConnector.submit(queuedDeclaration, isAmendment = false))
          .thenReturn(Future.successful(SubmissionResponse.Submitted))
        when(mockDeclarationsRepository.setSentToEtmp(queuedDeclaration.chargeReference, sentToEtmp = true))
          .thenReturn(Future.successful(queuedDeclaration))
        when(mockLockRepository.release(queuedDeclaration.chargeReference.value)).thenReturn(Future.unit)

        await(declarationSubmissionWorker.tap.pull()) mustBe Some((queuedDeclaration, SubmissionResponse.Submitted))

      }

      "throw a Fatal exception is thrown processing a declaration and stops the queue" in new Setup {

        when(mockDeclarationsRepository.paidDeclarationsForEtmp).thenReturn(Source(Vector(declaration)))
        when(mockLockRepository.lock(declaration.chargeReference.value))
          .thenThrow(new RuntimeException("Test Exception"))

        declarationSubmissionWorker.tap.pull().failed.map(_ mustBe an[ControlThrowable])

      }
    }
  }
}
