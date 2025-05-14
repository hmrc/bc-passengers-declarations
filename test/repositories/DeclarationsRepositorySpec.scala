/*
 * Copyright 2025 HM Revenue & Customs
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

package repositories

import helpers.Constants
import helpers.MongoTestUtils.{beEquivalentTo, givenAnExistingDocument, givenExistingDocuments}
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationResponse, DeclarationsStatus, PreviousDeclarationRequest}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.mongodb.scala.MongoCollection
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeclarationsRepositorySpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with DefaultPlayMongoRepositorySupport[Declaration]
    with Constants {

  private lazy val config: Configuration                              = app.injector.instanceOf[Configuration]
  private lazy val validationService                                  = app.injector.instanceOf[ValidationService]
  private lazy val mockChargeReferenceService: ChargeReferenceService = Mockito.mock(classOf[ChargeReferenceService])

  override protected val repository: DefaultDeclarationsRepository = new DefaultDeclarationsRepository(
    mongoComponent,
    chargeReferenceService = mockChargeReferenceService,
    validationService = validationService,
    config = config
  )

  implicit val inCollection: MongoCollection[Declaration] = repository.collection

  implicit lazy val materializer: Materializer = app.injector.instanceOf[Materializer]

  "DeclarationsRepository" when {
    ".insert" must {
      "write a declaration to mongodb" in {

        val nextChargeReference = ChargeReference(chargeReferenceNumber + 1)

        val updateChargeReference = declarationData.deepMerge(
          Json.obj(
            "simpleDeclarationRequest" -> Json.obj(
              "requestDetail" -> Json.obj(
                "declarationHeader" -> Json.obj(
                  "chargeReference" -> s"${nextChargeReference.toString}"
                )
              ),
              "requestCommon" -> Json.obj(
                "acknowledgementReference" -> s"${nextChargeReference.toString}0"
              )
            )
          )
        )

        val inputData: JsObject = Json.obj(
          "journeyData"              -> journeyData,
          "simpleDeclarationRequest" -> declarationData("simpleDeclarationRequest")
        )
        val expected            = declaration.copy(
          chargeReference = nextChargeReference,
          sentToEtmp = true,
          data = updateChargeReference
        )

        when(mockChargeReferenceService.nextChargeReference())
          .thenReturn(Future.successful(nextChargeReference))

        await(
          repository.insert(inputData, correlationId, sentToEtmp = true)
        ).toOption.get should beEquivalentTo(expected)

      }

      "return validation errors when receiving invalid data in a required field" in {
        val invalidRequiredData: JsObject =
          (declarationData \ "simpleDeclarationRequest").as[JsObject] - "requestCommon"

        val invalidInputData: JsObject = Json.obj(
          "journeyData"              -> journeyData,
          "simpleDeclarationRequest" -> invalidRequiredData
        )

        when(mockChargeReferenceService.nextChargeReference())
          .thenReturn(Future.successful(chargeReference))

        await(
          repository.insert(invalidInputData, correlationId, sentToEtmp = true)
        ).left.toOption.get.head shouldBe "object has missing required properties ([\"receiptDate\",\"requestParameters\"])"
      }
    }

    ".insertAmendment" must {
      "find and replace a declaration in mongodb" in {
        givenAnExistingDocument(declaration)

        val inputData: JsObject = Json.obj(
          "journeyData"              -> journeyData,
          "simpleDeclarationRequest" -> amendmentData.value("simpleDeclarationRequest")
        )
        val unpaidAmendment     = amendment.copy(state = State.PendingPayment)

        await(repository.insertAmendment(inputData, correlationId, chargeReference)) should beEquivalentTo(
          unpaidAmendment
        )
      }
    }

    ".get" that {
      "by ChargeReference" must {
        "return a declaration" in {
          givenAnExistingDocument(declaration)
          await(repository.get(chargeReference)).get shouldBe declaration
        }
      }

      "by PreviousDeclarationRequest" must {
        "returns a DeclarationsResponse" in {
          val paidDeclaration = declaration.copy(state = State.Paid)

          val declarationResponse = DeclarationResponse(
            "greatBritain",
            arrivingNI = true,
            isOver17 = true,
            isUKResident = Some(false),
            isPrivateTravel = true,
            userInformation,
            journeyData("calculatorResponse")("calculation").as[JsObject],
            liabilityDetails,
            journeyData("purchasedProductInstances").as[JsArray],
            amendmentCount = None,
            deltaCalculation = None,
            amendState = Some("None")
          )

          givenAnExistingDocument(paidDeclaration)

          val previousDeclaration =
            PreviousDeclarationRequest(userInformation("lastName").as[String], chargeReference.toString)

          await(repository.get(previousDeclaration)).get shouldBe declarationResponse
        }
      }

      "return None when no matching declaration is found" in {
        val previousDeclaration = PreviousDeclarationRequest("otherLastName", chargeReference.toString)

        await(repository.get(previousDeclaration)) shouldBe None
      }
    }

    ".remove" must {
      "delete an entry from mongodb" in {
        givenAnExistingDocument(declaration)

        await(repository.remove(chargeReference)).get shouldBe declaration
      }
    }

    ".setState" must {
      "update the payment status of a declaration" in {
        givenAnExistingDocument(declaration)

        val updatedDeclaration = declaration.copy(state = State.Paid)

        await(repository.setState(chargeReference, State.Paid)) should beEquivalentTo(updatedDeclaration)
      }
    }

    ".setAmendState" must {
      "update the payment status of an amended declaration" in {
        givenAnExistingDocument(amendment)

        val updatedAmendment: Declaration = amendment.copy(amendState = Some(State.Paid))

        await(repository.setAmendState(chargeReference, State.Paid)) should beEquivalentTo(updatedAmendment)
      }
    }

    ".setSentToEtmp" must {
      "update whether the declaration has been sent to Etmp" in {
        givenAnExistingDocument(declaration)

        val updatedDeclaration: Declaration = declaration.copy(sentToEtmp = true)

        await(repository.setSentToEtmp(chargeReference, sentToEtmp = true)) should beEquivalentTo(
          updatedDeclaration
        )
      }
    }

    ".setAmendSentToEtmp" must {
      "update whether the declaration has been sent to Etmp" in {
        givenAnExistingDocument(amendment)

        val updatedAmendment: Declaration = amendment.copy(amendSentToEtmp = Some(true))

        await(repository.setAmendSentToEtmp(chargeReference, amendSentToEtmp = true)) should beEquivalentTo(
          updatedAmendment
        )
      }
    }

    ".unpaidDeclarations" must {
      "return existing declarations with states other than paid" in {

        val paid      = declaration.copy(state = State.Paid)
        val pending   = declaration.copy(chargeReference = ChargeReference(1), state = State.PendingPayment)
        val cancelled = declaration.copy(chargeReference = ChargeReference(2), state = State.PaymentCancelled)
        val failed    = declaration.copy(chargeReference = ChargeReference(3), state = State.PaymentCancelled)

        givenExistingDocuments(List(paid, pending, cancelled, failed))

        val result = await(repository.unpaidDeclarations.runWith(Sink.seq))
        result should contain theSameElementsAs Seq(pending, cancelled, failed)

      }
    }

    ".unpaidAmendments" must {
      "return existing amendments with amendStates other than paid and were originally paid declarations" in {

        val paid             = amendment.copy(randomChargeReference(), state = State.Paid, amendState = Some(State.Paid))
        val previouslyUnpaid = amendment.copy(randomChargeReference(), state = State.PendingPayment)
        val pending          = amendment.copy(randomChargeReference(), amendState = Some(State.PendingPayment))
        val cancelled        = amendment.copy(randomChargeReference(), amendState = Some(State.PaymentCancelled))
        val failed           = amendment.copy(randomChargeReference(), amendState = Some(State.PaymentCancelled))

        givenExistingDocuments(List(paid, previouslyUnpaid, pending, cancelled, failed))

        val result = await(repository.unpaidAmendments.runWith(Sink.seq))
        result should contain theSameElementsAs Seq(pending, cancelled, failed)

      }
    }

    ".paidDeclarationsForEtmp" must {
      "return paid declarations which have not been sent to Etmp" in {

        val sentToEtmp    = declaration.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true)
        val notSentToEtmp = declaration.copy(randomChargeReference(), state = State.Paid, sentToEtmp = false)

        givenExistingDocuments(List(sentToEtmp, notSentToEtmp))

        val result = await(repository.paidDeclarationsForEtmp.runWith(Sink.seq))
        result should contain theSameElementsAs Seq(notSentToEtmp)

      }
    }

    ".paidAmendmentsForEtmp" must {
      "return paid amendments that have been not been sent to Etmp after amendment" in {
        val amendSentToEtmp                   = amendment.copy(
          randomChargeReference(),
          state = State.Paid,
          sentToEtmp = true,
          amendState = Some(State.Paid),
          amendSentToEtmp = Some(true)
        )
        val amendNotSentToEtmp                =
          amendment.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true, amendState = Some(State.Paid))
        val amendNotPaid                      =
          amendment.copy(
            randomChargeReference(),
            state = State.Paid,
            sentToEtmp = true,
            amendState = Some(State.PendingPayment)
          )
        val amendWithDeclarationNotSentToEtmp =
          amendment.copy(randomChargeReference(), state = State.Paid, sentToEtmp = false, amendState = Some(State.Paid))

        givenExistingDocuments(
          List(amendSentToEtmp, amendNotSentToEtmp, amendNotPaid, amendWithDeclarationNotSentToEtmp)
        )

        val result = await(repository.paidAmendmentsForEtmp.runWith(Sink.seq))

        result should contain theSameElementsAs Seq(amendNotSentToEtmp)

      }
    }

    ".paidDeclarationsForDeletion" must {
      "return paid declarations that have been sent to ETMP and paid amendments and sent to ETMP" in {
        val declarationSentToEtmp    = declaration.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true)
        val declarationNotSentToEtmp = declaration.copy(randomChargeReference(), state = State.Paid, sentToEtmp = false)
        val amendNotSentToEtmp       =
          amendment.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true, amendState = Some(State.Paid))
        val amendSentToEtmp          = amendment.copy(
          randomChargeReference(),
          state = State.Paid,
          sentToEtmp = true,
          amendState = Some(State.Paid),
          amendSentToEtmp = Some(true)
        )
        val amendNotPaid             = amendment.copy(
          randomChargeReference(),
          state = State.Paid,
          sentToEtmp = true,
          amendState = Some(State.PendingPayment)
        )

        givenExistingDocuments(
          List(declarationSentToEtmp, amendSentToEtmp, declarationNotSentToEtmp, amendNotSentToEtmp, amendNotPaid)
        )

        val result = await(repository.paidDeclarationsForDeletion.runWith(Sink.seq))

        result should contain theSameElementsAs Seq(declarationSentToEtmp, amendSentToEtmp)

      }
    }

    ".failedDeclarations" must {
      "return submissions that have failed" in {
        val submissionFailed = declaration.copy(randomChargeReference(), state = State.SubmissionFailed)

        givenExistingDocuments(List(submissionFailed, declaration))

        val result = await(repository.failedDeclarations.runWith(Sink.seq))

        result should contain theSameElementsAs Seq(submissionFailed)
      }
    }

    ".failedDeclarations" must {
      "return amendments that have failed" in {
        val submissionFailed = amendment.copy(randomChargeReference(), amendState = Some(State.SubmissionFailed))

        givenExistingDocuments(List(submissionFailed, amendment))

        val result = await(repository.failedAmendments.runWith(Sink.seq))

        result should contain theSameElementsAs Seq(submissionFailed)
      }
    }

    ".metricsCount" must {
      val paid          = declaration.copy(randomChargeReference(), state = State.Paid)
      val paymentFailed = declaration.copy(randomChargeReference(), state = State.PaymentFailed)
      val cancelled     = declaration.copy(chargeReference = randomChargeReference(), state = State.PaymentCancelled)
      val failed        = declaration.copy(chargeReference = randomChargeReference(), state = State.SubmissionFailed)

      "return a DeclarationStatus counting positive outcomes" in {
        givenExistingDocuments(List(declaration, paid))

        val result = await(repository.metricsCount.runWith(Sink.seq))
        result.head shouldBe DeclarationsStatus(1, 1, 0, 0, 0)
      }

      "return a DeclarationStatus counting negative outcomes" in {
        givenExistingDocuments(List(paymentFailed, cancelled, failed))

        val result = await(repository.metricsCount.runWith(Sink.seq))
        result.head shouldBe DeclarationsStatus(0, 0, 1, 1, 1)
      }

    }
  }
}
