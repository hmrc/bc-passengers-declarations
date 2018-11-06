package controllers

import models.declarations.{Declaration, State}
import models.ChargeReference
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

    "when given a valid request" - {

      "and mongo is available" - {

        "must return ACCEPTED and a Declaration" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, Json.obj())

          val requestBody = Json.obj("foo" -> "bar")

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody)

          when(declarationsRepository.insert(requestBody))
            .thenReturn(Future.successful(Right(declaration)))

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(declaration)

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).insert(requestBody)
          }
        }
      }

      "and mongo is unavailable" - {

        "must throw an exception" in {

          val requestBody = Json.obj("foo" -> "bar")

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody)

          when(declarationsRepository.insert(requestBody))
            .thenReturn(Future.failed(new Exception()))

          val result = route(app, request).value

          whenReady(result.failed) {
            _ mustBe an[Exception]
          }
        }
      }
    }

    "when given an invalid request" - {

      "must return BAD_REQUEST with a list of errors" in {

        val requestBody = Json.obj()

        val request = FakeRequest(POST, routes.DeclarationController.submit().url)
          .withJsonBody(requestBody)

        when(declarationsRepository.insert(requestBody))
          .thenReturn(Future.successful(Left(List("foo"))))

        val result = route(app, request).value

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustEqual Json.obj(
          "errors" -> Seq("foo")
        )
      }
    }
  }

  "update" - {

    "when a matching declaration is found" - {

      "and the declaration is locked" - {

        "must return LOCKED" in {

          val chargeReference = ChargeReference(1234567890)

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(false))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)

          val result = route(app, request).value

          status(result) mustBe LOCKED
          verify(lockRepository, never).release(1234567890)
        }
      }

      "and the declaration is in a Failed state" - {

        "must return CONFLICT" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.Failed, Json.obj())

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)

          val result = route(app, request).value

          status(result) mustBe CONFLICT
          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and the declaration is in a Paid state" - {

        "must return ACCEPTED without modifying the declaration" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.Paid, Json.obj())

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          verify(declarationsRepository, never()).setState(eqTo(chargeReference), any())
          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and updating its state fails" - {

        "must throw an exception" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, Json.obj())

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(declarationsRepository.setState(chargeReference, State.Paid))
            .thenReturn(Future.failed(new Exception))
          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)

          val result = route(app, request).value

          intercept[Exception] {
            status(result)
          }

          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and updating its state succeeds" - {

        "must return ACCEPTED" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, Json.obj())
          val updatedDeclaration = declaration copy (state = State.Paid)

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(declarationsRepository.setState(chargeReference, State.Paid))
            .thenReturn(Future.successful(updatedDeclaration))
          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)
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

        val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)
        val result = route(app, request).value

        status(result) mustBe NOT_FOUND

        whenReady(result) {
          _ =>
            verify(declarationsRepository, times(1)).get(chargeReference)
        }
      }
    }
  }
}
