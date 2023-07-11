/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationResponse, DeclarationsStatus, PreviousDeclarationRequest}
import org.mockito.MockitoSugar.{mock, when}
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions, ReplaceOneModel, ReplaceOptions}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.test.Helpers.await
import play.api.{Application, Configuration}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import util.Constants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DeclarationsRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with CleanMongoCollectionSupport
    with ScalaFutures
    with Eventually
    with Constants {

  override lazy val app: Application = new GuiceApplicationBuilder().build()

  implicit val timeout: Timeout                = Timeout(5.seconds)
  implicit lazy val materializer: Materializer = app.injector.instanceOf[Materializer]

  private lazy val config: Configuration = app.injector.instanceOf[Configuration]
  private lazy val mockValidationService = app.injector.instanceOf[ValidationService]
  private lazy val mockChargeReferenceService: ChargeReferenceService = mock[ChargeReferenceService]

  lazy val declarationsRepository                        = new DefaultDeclarationsRepository(
    mongoComponent = mongoComponent,
    chargeReferenceService = mockChargeReferenceService,
    validationService = mockValidationService,
    config = config
  )

  protected def collection: MongoCollection[Declaration] = declarationsRepository.collection

  "DeclarationsRepository" - {
    ".insert" - {
      "should write a declaration to mongodb" in {

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
        val expected = declaration.copy(
          chargeReference = nextChargeReference,
          sentToEtmp = true,
          data = updateChargeReference
        )

        when(mockChargeReferenceService.nextChargeReference())
          .thenReturn(Future.successful(nextChargeReference))

        await(
          declarationsRepository.insert(inputData, correlationId, sentToEtmp = true)
        ).toOption.get must beEquivalentTo(expected)

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
          declarationsRepository.insert(invalidInputData, correlationId, sentToEtmp = true)
        ).left.toOption.get.head mustBe "object has missing required properties ([\"receiptDate\",\"requestParameters\"])"
      }
    }

    ".insertAmendment" - {
      "should find and replace a declaration in mongodb" in {
        givenAnExistingDocument(declaration)

        val inputData: JsObject = Json.obj(
          "journeyData"              -> journeyData,
          "simpleDeclarationRequest" -> amendmentData.value("simpleDeclarationRequest")
        )
        val unpaidAmendment = amendment.copy(state = State.PendingPayment)

        await(declarationsRepository.insertAmendment(inputData, correlationId, chargeReference)) must beEquivalentTo(
          unpaidAmendment
        )
      }
    }

    ".get" - {
      "by ChargeReference" - {
        givenAnExistingDocument(declaration)
        await(declarationsRepository.get(chargeReference)).get mustBe declaration
      }

      "by PreviousDeclarationRequest" - {
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

          await(declarationsRepository.get(previousDeclaration)).get mustBe declarationResponse
        }
      }

      "returns None when no matching declaration is found" in {
        val previousDeclaration = PreviousDeclarationRequest("otherLastName", chargeReference.toString)

        await(declarationsRepository.get(previousDeclaration)) mustBe None
      }
    }

    ".remove" - {
      "deletes an entry from mongodb" in {
        givenAnExistingDocument(declaration)

        await(declarationsRepository.remove(chargeReference)).get mustBe declaration
      }
    }

    ".setState" - {
      "updates the payment status of a declaration" in {
        givenAnExistingDocument(declaration)

        val updatedDeclaration = declaration.copy(state = State.Paid)

        await(declarationsRepository.setState(chargeReference, State.Paid)) must beEquivalentTo(updatedDeclaration)
      }
    }

    ".setAmendState" - {
      "updates the payment status of an amended declaration" in {
        givenAnExistingDocument(amendment)

        val updatedAmendment: Declaration = amendment.copy(amendState = Some(State.Paid))

        await(declarationsRepository.setAmendState(chargeReference, State.Paid)) must beEquivalentTo(updatedAmendment)
      }
    }

    ".setSentToEtmp" - {
      "updates whether the declaration has been sent to Etmp" in {
        givenAnExistingDocument(declaration)

        val updatedDeclaration: Declaration = declaration.copy(sentToEtmp = true)

        await(declarationsRepository.setSentToEtmp(chargeReference, sentToEtmp = true)) must beEquivalentTo(
          updatedDeclaration
        )
      }
    }

    ".setAmendSentToEtmp" - {
      "updates whether the declaration has been sent to Etmp" in {
        givenAnExistingDocument(amendment)

        val updatedAmendment: Declaration = amendment.copy(amendSentToEtmp = Some(true))

        await(declarationsRepository.setAmendSentToEtmp(chargeReference, amendSentToEtmp = true)) must beEquivalentTo(
          updatedAmendment
        )
      }
    }

    ".unpaidDeclarations" - {
      "returns existing declarations with states other than paid" in {

        val paid      = declaration.copy(state = State.Paid)
        val pending   = declaration.copy(chargeReference = ChargeReference(1), state = State.PendingPayment)
        val cancelled = declaration.copy(chargeReference = ChargeReference(2), state = State.PaymentCancelled)
        val failed    = declaration.copy(chargeReference = ChargeReference(3), state = State.PaymentCancelled)

        givenExistingDocuments(List(paid, pending, cancelled, failed))

        val result = await(declarationsRepository.unpaidDeclarations.runWith(Sink.seq))
        result must contain theSameElementsAs Seq(pending, cancelled, failed)

      }
    }

    ".unpaidAmendments" - {
      "returns existing amendments with amendStates other than paid and were originally paid declarations" in {

        val paid             = amendment.copy(randomChargeReference(), state = State.Paid, amendState = Some(State.Paid))
        val previouslyUnpaid = amendment.copy(randomChargeReference(), state = State.PendingPayment)
        val pending          = amendment.copy(randomChargeReference(), amendState = Some(State.PendingPayment))
        val cancelled        = amendment.copy(randomChargeReference(), amendState = Some(State.PaymentCancelled))
        val failed           = amendment.copy(randomChargeReference(), amendState = Some(State.PaymentCancelled))

        givenExistingDocuments(List(paid, previouslyUnpaid, pending, cancelled, failed))

        val result = await(declarationsRepository.unpaidAmendments.runWith(Sink.seq))
        result must contain theSameElementsAs Seq(pending, cancelled, failed)

      }
    }

    ".paidDeclarationsForEtmp" - {
      "returns paid declarations which have not been sent to Etmp" in {

        val sentToEtmp    = declaration.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true)
        val notSentToEtmp = declaration.copy(randomChargeReference(), state = State.Paid, sentToEtmp = false)

        givenExistingDocuments(List(sentToEtmp, notSentToEtmp))

        val result = await(declarationsRepository.paidDeclarationsForEtmp.runWith(Sink.seq))
        result must contain theSameElementsAs Seq(notSentToEtmp)

      }
    }

    ".paidAmendmentsForEtmp" - {
      "returns paid amendments that have been not been sent to Etmp after amendment" in {
        val amendSentToEtmp                   = amendment.copy(randomChargeReference(),
          state = State.Paid,
          sentToEtmp = true,
          amendState = Some(State.Paid),
          amendSentToEtmp = Some(true)
        )
        val amendNotSentToEtmp                = amendment.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true, amendState = Some(State.Paid))
        val amendNotPaid                      =
          amendment.copy(randomChargeReference(), state = State.Paid, sentToEtmp = true, amendState = Some(State.PendingPayment))
        val amendWithDeclarationNotSentToEtmp =
          amendment.copy(randomChargeReference(), state = State.Paid, sentToEtmp = false, amendState = Some(State.Paid))

        givenExistingDocuments(
          List(amendSentToEtmp, amendNotSentToEtmp, amendNotPaid, amendWithDeclarationNotSentToEtmp)
        )

        val result = await(declarationsRepository.paidAmendmentsForEtmp.runWith(Sink.seq))

        result must contain theSameElementsAs Seq(amendNotSentToEtmp)

      }
    }

    ".paidDeclarationsForDeletion" - {
      "returns paid declarations that have been sent to ETMP and paid amendments and sent to ETMP" in {

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

        val result = await(declarationsRepository.paidDeclarationsForDeletion.runWith(Sink.seq))

        result must contain theSameElementsAs Seq(declarationSentToEtmp, amendSentToEtmp)

      }
    }

    ".failedDeclarations" - {
      "return submissions that have failed" in {
        val submissionFailed = declaration.copy(randomChargeReference(), state = State.SubmissionFailed)

        givenExistingDocuments(List(submissionFailed, declaration))

        val result = await(declarationsRepository.failedDeclarations.runWith(Sink.seq))

        result must contain theSameElementsAs Seq(submissionFailed)
      }
    }

    ".failedDeclarations" - {
      "return amendments that have failed" in {
        val submissionFailed = amendment.copy(randomChargeReference(), amendState = Some(State.SubmissionFailed))

        givenExistingDocuments(List(submissionFailed, amendment))

        val result = await(declarationsRepository.failedAmendments.runWith(Sink.seq))

        result must contain theSameElementsAs Seq(submissionFailed)
      }
    }

    ".metricsCount" - {

      val paid      = declaration.copy(randomChargeReference(), state = State.Paid)
      val paymentFailed = declaration.copy(randomChargeReference(), state = State.PaymentFailed)
      val cancelled = declaration.copy(chargeReference = randomChargeReference(), state = State.PaymentCancelled)
      val failed = declaration.copy(chargeReference = randomChargeReference(), state = State.SubmissionFailed)

      "returns a DeclarationStatus counting positive outcomes" in {
        givenExistingDocuments(List(declaration, paid))

        val result = await(declarationsRepository.metricsCount.runWith(Sink.seq))

        result.head mustBe DeclarationsStatus(1, 1, 0, 0, 0)
      }

      "returns a DeclarationStatus counting negative outcomes" in {
        givenExistingDocuments(List(paymentFailed, cancelled, failed))

        val result = await(declarationsRepository.metricsCount.runWith(Sink.seq))

        result.head mustBe DeclarationsStatus(0, 0, 1, 1, 1)
      }

    }

  }

  private def givenAnExistingDocument(declaration: Declaration): Unit = {
    val selector = Filters.equal("_id", declaration.chargeReference.toString)

    val result = collection
      .findOneAndReplace(selector, declaration, FindOneAndReplaceOptions().upsert(true))
      .toFuture()
      .flatMap(_ => Future.unit)

    await(result)
  }

  private def givenExistingDocuments(declarations: List[Declaration]): Unit = {
    val updates = declarations.map { declaration =>
      val selector = Filters.equal("_id", declaration.chargeReference.toString)
      ReplaceOneModel(selector, declaration, ReplaceOptions().upsert(true))
    }

    val result = collection
      .bulkWrite(updates)
      .toFuture()
      .flatMap(_ => Future.unit)

    await(result)
  }

  def beEquivalentTo(expected: Declaration): Matcher[Declaration] = (result: Declaration) => {
    val withoutLastUpdated = result.copy(lastUpdated = expected.lastUpdated)
    val areEquivalent      = withoutLastUpdated == expected
    val explanation        = if (areEquivalent) "" else s"$expected was not equal to $result"
    MatchResult(areEquivalent, explanation, explanation)
  }
}
