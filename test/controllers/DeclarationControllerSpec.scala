/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package controllers

import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationResponse, PreviousDeclarationRequest}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo, _}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{DeclarationsRepository, LockRepository}

import scala.concurrent.Future

class DeclarationControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite
  with OptionValues with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val declarationsRepository = mock[DeclarationsRepository]

  private val lockRepository = mock[LockRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(declarationsRepository)
    Mockito.reset(lockRepository)
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

  "submit" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "when given a valid request" - {

      "and mongo is available" - {

        "must return ACCEPTED and a Declaration" in {

          val chargeReference = ChargeReference(1234567890)

          val message = Json.obj("simpleDeclarationRequest" -> Json.obj("foo" -> "bar"))

          val journeyData = Json.obj("foo" -> "bar")

          val declaration = Declaration(chargeReference, State.PendingPayment, None, sentToEtmp = false, None, correlationId, journeyData, message)

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(message).withHeaders("X-Correlation-ID" -> correlationId)

          when(declarationsRepository.insert(message, correlationId, sentToEtmp = false))
            .thenReturn(Future.successful(Right(declaration)))

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          headers(result) must contain ("X-Correlation-ID" -> correlationId)
          contentAsJson(result) mustBe declaration.data

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).insert(message, correlationId,sentToEtmp = false)
          }
        }
      }

      "and mongo is unavailable" - {

        "must throw an exception" in {

          val requestBody = Json.obj("foo" -> "bar")

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody).withHeaders("X-Correlation-ID" -> correlationId)

          when(declarationsRepository.insert(requestBody, correlationId, sentToEtmp = false))
            .thenReturn(Future.failed(new Exception()))

          val result = route(app, request).value

          whenReady(result.failed) {
            _ mustBe an[Exception]
          }
        }
      }
    }

    "when given an invalid request" - {

      "must return BAD_REQUEST when not supplied with a correlation id in the headers" in {

        val requestBody = Json.obj()

        val chargeReference = ChargeReference(1234567890)

        val declaration = Declaration(chargeReference, State.PendingPayment, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())

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

      "must return BAD_REQUEST with a list of errors" in {

        val requestBody = Json.obj()

        val request = FakeRequest(POST, routes.DeclarationController.submit().url)
          .withJsonBody(requestBody).withHeaders("X-Correlation-ID" -> correlationId)

        when(declarationsRepository.insert(requestBody, correlationId,sentToEtmp = false))
          .thenReturn(Future.successful(Left(List("foo"))))

        val result = route(app, request).value

        status(result) mustBe BAD_REQUEST
        headers(result) must contain ("X-Correlation-ID" -> correlationId)
        contentAsJson(result) mustEqual Json.obj(
          "errors" -> Seq("foo")
        )
      }
    }
  }

  "update" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Successful")

    "when a matching declaration is found" - {

      "and the declaration is locked" - {

        "must return LOCKED" in {

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(false))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

          val result = route(app, request).value

          status(result) mustBe LOCKED
          verify(lockRepository, never).release(1234567890)
        }
      }

      "and the declaration is in a Failed state" - {

        "must return CONFLICT" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.SubmissionFailed, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

          val result = route(app, request).value

          status(result) mustBe CONFLICT
          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and the declaration is in a Paid state" - {

        "must return ACCEPTED without modifying the declaration" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.Paid, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          verify(declarationsRepository, never()).setState(eqTo(chargeReference), any())
          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and updating its state fails" - {

        "must throw an exception" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(declarationsRepository.setState(chargeReference, State.Paid))
            .thenReturn(Future.failed(new Exception))
          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

          val result = route(app, request).value

          intercept[Exception] {
            status(result)
          }

          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and the payload status is Successful and updating its state succeeds" - {

        "must return ACCEPTED and update the state to Paid" in {

          val chargeReference = ChargeReference(1234567890)

          val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Successful")

          val declaration = Declaration(chargeReference, State.PendingPayment, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
          val updatedDeclaration = declaration copy (state = State.Paid)

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(declarationsRepository.setState(chargeReference, State.Paid))
            .thenReturn(Future.successful(updatedDeclaration))
          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
          val result = route(app, request).value

          status(result) mustBe ACCEPTED

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setState(chargeReference, State.Paid)
              verify(lockRepository, times(1)).release(1234567890)
          }
        }
      }

      "and the payload status is Failed and updating its state succeeds" - {

        "must return ACCEPTED and update the state to PaymentFailed" in {

          val chargeReference = ChargeReference(1234567890)

          val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Failed")

          val declaration = Declaration(chargeReference, State.PendingPayment, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
          val updatedDeclaration = declaration copy (state = State.PaymentFailed)

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(declarationsRepository.setState(chargeReference, State.PaymentFailed))
            .thenReturn(Future.successful(updatedDeclaration))
          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
          val result = route(app, request).value

          status(result) mustBe ACCEPTED

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setState(chargeReference, State.PaymentFailed)
              verify(lockRepository, times(1)).release(1234567890)
          }
        }
      }

      "and the payload status is Cancelled and updating its state succeeds" - {

        "must return ACCEPTED and update the state to PaymentCancelled" in {

          val chargeReference = ChargeReference(1234567890)

          val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Cancelled")

          val declaration = Declaration(chargeReference, State.PendingPayment, None, sentToEtmp = false, None, correlationId, Json.obj(), Json.obj())
          val updatedDeclaration = declaration copy (state = State.PaymentCancelled)

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(declarationsRepository.setState(chargeReference, State.PaymentCancelled))
            .thenReturn(Future.successful(updatedDeclaration))
          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
          val result = route(app, request).value

          status(result) mustBe ACCEPTED

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).get(chargeReference)
              verify(declarationsRepository, times(1)).setState(chargeReference, State.PaymentCancelled)
              verify(lockRepository, times(1)).release(1234567890)
          }
        }
      }
    }

    "when a matching declaration is not found" - {

      "must return NOT_FOUND" in {

        val chargeReference = ChargeReference(1234567890)

        when(declarationsRepository.get(chargeReference))
          .thenReturn(Future.successful(None))
        when(lockRepository.lock(1234567890))
          .thenReturn(Future.successful(true))
        when(lockRepository.release(1234567890))
          .thenReturn(Future.successful(()))

        val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
        val result = route(app, request).value

        status(result) mustBe NOT_FOUND

        whenReady(result) {
          _ =>
            verify(declarationsRepository, times(1)).get(chargeReference)
        }
      }
    }

    "when a request is made with an invalid charge reference" - {

      "must return BAD_REQUEST" in {

        val jsonPayload = Json.obj("reference" -> "XDDD0000000105", "status" -> "Successful")
        val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }
    }
  }

  "retrieve" - {

    "when given a valid request" - {

      "and mongo is available" - {

        "must return OK and a matched Declaration" in {

          val input = PreviousDeclarationRequest("POTTER", "SX12345", "1234567890")

          val declarationResponse = DeclarationResponse("greatBritain", false, true, Some(true), false, Json.obj("calculation" -> "somecalcultaion"), Json.arr("oldPurchaseProductInstances" -> Json.obj()))

          when(declarationsRepository.get(input))
            .thenReturn(Future.successful(Some(declarationResponse)))

          val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url).withJsonBody(Json.toJsObject(input))

          val result = route(app, request).value

          status(result) mustBe OK
          contentAsJson(result) mustBe Json.toJsObject(declarationResponse)

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).get(input)
          }
        }
      }

      "and mongo is unavailable" - {

        "must throw an exception" in {

          val input = PreviousDeclarationRequest("POTTER", "SX12345", "1234567890")

          when(declarationsRepository.get(input))
            .thenReturn(Future.failed(new Exception()))

          val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url).withJsonBody(Json.toJsObject(input))

          val result = route(app, request).value

          whenReady(result.failed) {
            _ mustBe an[Exception]
          }
        }
      }
    }

    "when given an invalid request" - {

      "must return BAD_REQUEST when not supplied with a correlation id in the headers" in {

        val requestBody = Json.obj()

        val input = PreviousDeclarationRequest("POTTER", "SX12345", "1234567890")

        val declarationResponse = DeclarationResponse("greatBritain", false, true, Some(true), false, Json.obj("calculation" -> "somecalcultaion"), Json.arr("oldPurchaseProductInstances" -> Json.obj()))

        val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url)
          .withJsonBody(requestBody)

        when(declarationsRepository.get(input))
          .thenReturn(Future.successful(Some(declarationResponse)))

        val result = route(app, request).value

        status(result) mustBe BAD_REQUEST
      }
    }

    "when a matching declaration is not found" - {

      "must return NOT_FOUND" in {

        val input = PreviousDeclarationRequest("POTTER", "SX12345", "1234567890")

        when(declarationsRepository.get(input))
          .thenReturn(Future.successful(None))

        val request = FakeRequest(POST, routes.DeclarationController.retrieveDeclaration().url).withJsonBody(Json.toJsObject(input))

        val result = route(app, request).value

        status(result) mustBe NOT_FOUND

        whenReady(result) {
          _ =>
            verify(declarationsRepository, times(1)).get(input)
        }
      }
    }
  }
}
