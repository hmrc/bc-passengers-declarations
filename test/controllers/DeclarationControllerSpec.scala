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

package controllers

import helpers.Constants
import models.declarations.State
import models.{DeclarationResponse, PreviousDeclarationRequest}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.Future

class DeclarationControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach
    with Constants {

  private val declarationsRepository = mock[DeclarationsRepository]
  private val lockRepository         = mock[LockRepository]

  override def beforeEach(): Unit = {
    reset(declarationsRepository)
    reset(lockRepository)
  }

  override lazy val app: Application = {

    import play.api.inject._

    new GuiceApplicationBuilder()
      .overrides(
        bind[DeclarationsRepository].toInstance(declarationsRepository),
        bind[LockRepository].toInstance(lockRepository)
      )
      .build()
  }

  "DeclarationController" should {
    ".submit" when {
      "given a valid request" when {
        "mongo is available" must {
          "return ACCEPTED and a Declaration" in {

            val request = FakeRequest(POST, routes.DeclarationController.submit().url)
              .withJsonBody(declarationData)
              .withHeaders("X-Correlation-ID" -> correlationId)

            when(declarationsRepository.insert(declarationData, correlationId, sentToEtmp = false))
              .thenReturn(Future.successful(Right(declaration)))

            val result = route(app, request).value

            status(result) mustBe ACCEPTED
            headers(result) must contain("X-Correlation-ID" -> correlationId)
            contentAsJson(result) mustBe declaration.data

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).insert(declarationData, correlationId, sentToEtmp = false)
            }
          }
        }

        "mongo is unavailable" must {
          "throw an exception" in {

            val requestBody = Json.obj("foo" -> "bar")

            val request = FakeRequest(POST, routes.DeclarationController.submit().url)
              .withJsonBody(requestBody)
              .withHeaders("X-Correlation-ID" -> correlationId)

            when(declarationsRepository.insert(requestBody, correlationId, sentToEtmp = false))
              .thenReturn(Future.failed(new Exception()))

            val result = route(app, request).value

            whenReady(result.failed) {
              _ mustBe an[Exception]
            }
          }
        }
      }

      "given an invalid request" must {
        "return BAD_REQUEST when not supplied with a correlation id in the headers" in {

          val requestBody = Json.obj()

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody)

          when(declarationsRepository.insert(requestBody, correlationId, sentToEtmp = false))
            .thenReturn(Future.successful(Right(declaration)))

          val result = route(app, request).value

          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustEqual Json.obj(
            "errors" -> Seq("Missing X-Correlation-ID header")
          )
        }

        "return BAD_REQUEST with a list of errors" in {

          val requestBody = Json.obj()

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody)
            .withHeaders("X-Correlation-ID" -> correlationId)

          when(declarationsRepository.insert(requestBody, correlationId, sentToEtmp = false))
            .thenReturn(Future.successful(Left(List("foo"))))

          val result = route(app, request).value

          status(result) mustBe BAD_REQUEST
          headers(result) must contain("X-Correlation-ID" -> correlationId)
          contentAsJson(result) mustEqual Json.obj(
            "errors" -> Seq("foo")
          )
        }
      }
    }

    ".submitAmendment" when {
      "when given a valid request" when {
        "mongo is available" must {
          "be an updated Declaration with Amendment" in {

            val inputAmendmentData: JsObject = Json.obj(
              "journeyData"              -> journeyData,
              "simpleDeclarationRequest" -> amendmentData("simpleDeclarationRequest")
            )

            val request = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
              .withJsonBody(inputAmendmentData)
              .withHeaders("X-Correlation-ID" -> correlationId)

            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))
            when(declarationsRepository.insertAmendment(inputAmendmentData, correlationId, chargeReference))
              .thenReturn(Future.successful(amendment))

            val result = route(app, request).value

            status(result) mustBe ACCEPTED
            headers(result) must contain("X-Correlation-ID" -> correlationId)
            contentAsJson(result) mustBe amendment.amendData.get

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1))
                .insertAmendment(inputAmendmentData, correlationId, chargeReference)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }

        "mongo is unavailable" must {
          "throw an exception" in {

            val requestBody = Json.obj("foo" -> "bar")

            val request = FakeRequest(POST, routes.DeclarationController.submit().url)
              .withJsonBody(requestBody)
              .withHeaders("X-Correlation-ID" -> correlationId)

            when(declarationsRepository.insert(requestBody, correlationId, sentToEtmp = false))
              .thenReturn(Future.failed(new Exception()))

            val result = route(app, request).value

            whenReady(result.failed) {
              _ mustBe an[Exception]
            }
          }
        }
      }

      "given an invalid request" must {
        "throw an exception when there is an invalid chargeReference id in amendment data" in {

          val invalidData: JsObject = amendmentData.deepMerge(
            Json.obj(
              "simpleDeclarationRequest" -> Json.obj(
                "requestDetail" -> Json.obj(
                  "declarationHeader" -> Json.obj(
                    "chargeReference" -> "AAAAAAAAAAAAAA"
                  )
                )
              )
            )
          )

          val inputInvalidAmendmentData: JsObject = Json.obj(
            "journeyData"              -> journeyData,
            "simpleDeclarationRequest" -> invalidData("simpleDeclarationRequest")
          )

          val request   = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
            .withJsonBody(inputInvalidAmendmentData)
            .withHeaders("X-Correlation-ID" -> correlationId)

          val exception = route(app, request).value

          val result = intercept[Exception] {
            status(exception)
          }

          result.getMessage mustBe "unable to extract charge reference:AAAAAAAAAAAAAA"
        }

        "return BAD_REQUEST when not supplied with a correlation id in the headers" in {

          val requestBody = Json.obj()

          val request = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
            .withJsonBody(requestBody)

          when(declarationsRepository.insertAmendment(requestBody, correlationId, chargeReference))
            .thenReturn(Future.successful(amendment))

          val result = route(app, request).value

          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustEqual Json.obj(
            "errors" -> Seq("Missing X-Correlation-ID header")
          )
        }

        "return BAD_REQUEST with errors" in {

          val requestBody = Json.obj()

          val request = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
            .withJsonBody(requestBody)
            .withHeaders("X-Correlation-ID" -> correlationId)

          val result  = route(app, request).value

          status(result) mustBe BAD_REQUEST
          headers(result)                must contain("X-Correlation-ID" -> correlationId)
          contentAsJson(result).toString must include(
            "object has too few properties (found 0 but schema requires at least 1)"
          )
        }
      }
    }

    ".update" when {

      val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Successful")

      "a matching declaration is found" when {
        "the declaration is locked" must {
          "return LOCKED" in {

            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(false))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            status(result) mustBe LOCKED
            verify(lockRepository, never).release(chargeReferenceNumber)
          }
        }

        "the declaration is in a Failed state" must {
          "return CONFLICT" in {

            val declarationSubmissionFailed = declaration.copy(state = State.SubmissionFailed)

            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))
            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(declarationSubmissionFailed)))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            status(result) mustBe CONFLICT
            verify(lockRepository, times(1)).release(chargeReferenceNumber)
          }
        }

        "the declaration is in a Paid state" must {
          "return ACCEPTED without modifying the declaration" in {

            val declarationPaid = declaration.copy(state = State.Paid)

            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))
            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(declarationPaid)))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            status(result) mustBe ACCEPTED
            verify(declarationsRepository, never).setState(eqTo(chargeReference), any())
            verify(lockRepository, times(1)).release(chargeReferenceNumber)
          }
        }

        "updating its state fails" must {
          "throw an exception" in {

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(declaration)))
            when(declarationsRepository.setState(chargeReference, State.Paid))
              .thenReturn(Future.failed(new Exception))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            intercept[Exception] {
              status(result)
            }

            verify(lockRepository, times(1)).release(chargeReferenceNumber)
          }
        }

        "the payload status is Successful and updating its state succeeds" must {
          "return ACCEPTED and update the state to Paid" in {

            val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Successful")

            val updatedDeclaration = declaration.copy(state = State.Paid)

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(declaration)))
            when(declarationsRepository.setState(chargeReference, State.Paid))
              .thenReturn(Future.successful(updatedDeclaration))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
            val result  = route(app, request).value

            status(result) mustBe ACCEPTED

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setState(chargeReference, State.Paid)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }

        "the payload status is Failed and updating its state succeeds" must {
          "return ACCEPTED and update the state to PaymentFailed" in {

            val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Failed")

            val updatedDeclaration = declaration.copy(state = State.PaymentFailed)

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(declaration)))
            when(declarationsRepository.setState(chargeReference, State.PaymentFailed))
              .thenReturn(Future.successful(updatedDeclaration))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
            val result  = route(app, request).value

            status(result) mustBe ACCEPTED

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setState(chargeReference, State.PaymentFailed)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }

        "the payload status is Cancelled and updating its state succeeds" must {
          "return ACCEPTED and update the state to PaymentCancelled" in {

            val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Cancelled")

            val updatedDeclaration = declaration.copy(state = State.PaymentCancelled)

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(declaration)))
            when(declarationsRepository.setState(chargeReference, State.PaymentCancelled))
              .thenReturn(Future.successful(updatedDeclaration))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
            val result  = route(app, request).value

            status(result) mustBe ACCEPTED

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setState(chargeReference, State.PaymentCancelled)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }
      }

      "a matching declaration is not found" must {
        "return NOT_FOUND" in {

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(None))
          when(lockRepository.lock(chargeReferenceNumber))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(chargeReferenceNumber))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
          val result  = route(app, request).value

          status(result) mustBe NOT_FOUND

          whenReady(result) { _ =>
            verify(declarationsRepository, times(1)).get(chargeReference)
          }
        }
      }

      "a request is made with an invalid charge reference" must {
        "return BAD_REQUEST" in {

          val jsonPayload = Json.obj("reference" -> "XDDD0000000105", "status" -> "Successful")
          val request     = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
          val result      = route(app, request).value
          status(result) mustBe BAD_REQUEST
        }
      }
    }

    ".update (amendment state)" when {

      val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Successful")

      "a matching amendment is found" when {
        "the amendment is in a Failed state" must {
          "return CONFLICT" in {

            val amendmentDeclarationSubmissionFailed =
              amendment.copy(amendState = Some(State.SubmissionFailed))

            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))
            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(amendmentDeclarationSubmissionFailed)))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            status(result) mustBe CONFLICT
            verify(lockRepository, times(1)).release(chargeReferenceNumber)
          }
        }

        "the amendment is in a Paid state" must {
          "return ACCEPTED without modifying the amendment" in {

            val amendmentDeclarationPaid = amendment.copy(amendState = Some(State.Paid))

            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))
            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(amendmentDeclarationPaid)))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            status(result) mustBe ACCEPTED
            verify(declarationsRepository, never).setAmendState(eqTo(chargeReference), any())
            verify(lockRepository, times(1)).release(chargeReferenceNumber)
          }
        }

        "updating its amendState fails" must {
          "throw an exception" in {

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(amendment)))
            when(declarationsRepository.setAmendState(chargeReference, State.Paid))
              .thenReturn(Future.failed(new Exception))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

            val result = route(app, request).value

            intercept[Exception] {
              status(result)
            }

            verify(lockRepository, times(1)).release(chargeReferenceNumber)
          }
        }

        "the payload status is Successful and updating its amendState succeeds" must {
          "return ACCEPTED and update the amendState to Paid" in {

            val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Successful")

            val updatedAmendment = amendment.copy(amendState = Some(State.Paid))

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(amendment)))
            when(declarationsRepository.setAmendState(chargeReference, State.Paid))
              .thenReturn(Future.successful(updatedAmendment))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
            val result  = route(app, request).value

            status(result) mustBe ACCEPTED

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setAmendState(chargeReference, State.Paid)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }

        "the payload status is Failed and updating its amendState succeeds" must {

          "return ACCEPTED and update the amendState to PaymentFailed" in {

            val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Failed")

            val updatedAmendment = amendment.copy(amendState = Some(State.PaymentFailed))

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(amendment)))
            when(declarationsRepository.setAmendState(chargeReference, State.PaymentFailed))
              .thenReturn(Future.successful(updatedAmendment))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
            val result  = route(app, request).value

            status(result) mustBe ACCEPTED

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setAmendState(chargeReference, State.PaymentFailed)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }

        "the payload status is Cancelled and updating its amendState succeeds" must {

          "return ACCEPTED and update the amendState to PaymentCancelled" in {

            val jsonPayload = Json.obj("reference" -> chargeReference, "status" -> "Cancelled")

            val updatedAmendment = amendment.copy(amendState = Some(State.PaymentCancelled))

            when(declarationsRepository.get(chargeReference))
              .thenReturn(Future.successful(Some(amendment)))
            when(declarationsRepository.setAmendState(chargeReference, State.PaymentCancelled))
              .thenReturn(Future.successful(updatedAmendment))
            when(lockRepository.lock(chargeReferenceNumber))
              .thenReturn(Future.successful(true))
            when(lockRepository.release(chargeReferenceNumber))
              .thenReturn(Future.successful(()))

            val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
            val result  = route(app, request).value

            status(result) mustBe ACCEPTED

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setAmendState(chargeReference, State.PaymentCancelled)
              verify(lockRepository, times(1)).release(chargeReferenceNumber)
            }
          }
        }
      }
    }

    ".retrieveDeclaration" when {
      "given a valid request" when {
        "mongo is available" must {
          "return OK and a matched Declaration" in {

            val input = PreviousDeclarationRequest("POTTER", "1234567890")

            val declarationResponse = DeclarationResponse(
              "greatBritain",
              arrivingNI = false,
              isOver17 = true,
              isUKResident = Some(true),
              isPrivateTravel = false,
              Json.obj("userInformation"             -> "someUserInformation"),
              Json.obj("calculation"                 -> "somecalcultaion"),
              Json.obj("liabilityDetails"            -> "SomeLiability"),
              Json.arr("oldPurchaseProductInstances" -> Json.obj()),
              amendmentCount = Some(0),
              Some(Json.obj("deltaCalculation" -> "somecalcultaion")),
              amendState = Some("")
            )

            when(declarationsRepository.get(input))
              .thenReturn(Future.successful(Some(declarationResponse)))

            val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url)
              .withJsonBody(Json.toJsObject(input))

            val result = route(app, request).value

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.toJsObject(declarationResponse)

            whenReady(result) { _ =>
              verify(declarationsRepository, times(1)).get(input)
            }
          }
        }

        "mongo is unavailable" must {
          "throw an exception" in {

            val input = PreviousDeclarationRequest("POTTER", "1234567890")

            when(declarationsRepository.get(input))
              .thenReturn(Future.failed(new Exception()))

            val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url)
              .withJsonBody(Json.toJsObject(input))

            val result = route(app, request).value

            whenReady(result.failed) {
              _ mustBe an[Exception]
            }
          }
        }
      }

      "given an invalid request" must {
        "return BAD_REQUEST when not supplied with a correlation id in the headers" in {

          val requestBody = Json.obj()

          val input = PreviousDeclarationRequest("POTTER", "1234567890")

          val declarationResponse = DeclarationResponse(
            "greatBritain",
            arrivingNI = false,
            isOver17 = true,
            isUKResident = Some(true),
            isPrivateTravel = false,
            Json.obj("userInformation"             -> "someUserInformation"),
            Json.obj("calculation"                 -> "somecalcultaion"),
            Json.obj("liabilityDetails"            -> "SomeLiability"),
            Json.arr("oldPurchaseProductInstances" -> Json.obj()),
            amendmentCount = Some(0),
            Some(Json.obj("deltaCalculation" -> "somecalcultaion")),
            amendState = Some("")
          )

          val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url)
            .withJsonBody(requestBody)

          when(declarationsRepository.get(input))
            .thenReturn(Future.successful(Some(declarationResponse)))

          val result = route(app, request).value

          status(result) mustBe BAD_REQUEST
        }
      }

      "a matching declaration is not found" must {
        "return NOT_FOUND" in {

          val input = PreviousDeclarationRequest("POTTER", "1234567890")

          when(declarationsRepository.get(input))
            .thenReturn(Future.successful(None))

          val request =
            FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url)
              .withJsonBody(Json.toJsObject(input))

          val result = route(app, request).value

          status(result) mustBe NOT_FOUND

          whenReady(result) { _ =>
            verify(declarationsRepository, times(1)).get(input)
          }
        }
      }
    }
  }
}
