package controllers

import models.declarations.{Declaration, State}
import models.ChargeReference
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.DeclarationsRepository

import scala.concurrent.Future

class DeclarationControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite
  with OptionValues with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val repository = mock[DeclarationsRepository]

  override def beforeEach(): Unit =
    Mockito.reset(repository)

  override lazy val app: Application = {

    import play.api.inject._

    new GuiceApplicationBuilder()
      .overrides(
        bind[DeclarationsRepository].toInstance(repository)
      )
      .build()
  }

  "submit" - {

    "when given a valid request" - {

      "and mongo is available" - {

        "must return ACCEPTED and a Declaration" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, Json.obj())

          val requestBody = Json.obj()

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody)

          when(repository.insert(requestBody))
            .thenReturn(Future.successful(declaration))

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(declaration)

          whenReady(result) {
            _ =>
              verify(repository, times(1)).insert(requestBody)
          }
        }
      }

      "and mongo is unavailable" - {

        "must throw an exception" in {

          val requestBody = Json.obj()

          val request = FakeRequest(POST, routes.DeclarationController.submit().url)
            .withJsonBody(requestBody)

          when(repository.insert(requestBody))
            .thenReturn(Future.failed(new Exception()))

          val result = route(app, request).value

          whenReady(result.failed) {
            _ mustBe an[Exception]
          }
        }
      }
    }
  }

  "update" - {

    "when a matching declaration is found" - {

      "and updating its state fails" - {

        "must throw an exception" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, Json.obj())

          when(repository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(repository.setState(chargeReference, State.Paid))
            .thenReturn(Future.failed(new Exception))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)

          val result = route(app, request).value

          intercept[Exception] {
            status(result)
          }
        }
      }

      "and updating its state succeeds" - {

        "must return ACCEPTED" in {

          val chargeReference = ChargeReference(1234567890)

          val declaration = Declaration(chargeReference, State.PendingPayment, Json.obj())
          val updatedDeclaration = declaration copy (state = State.Paid)

          when(repository.get(chargeReference))
            .thenReturn(Future.successful(Some(declaration)))
          when(repository.setState(chargeReference, State.Paid))
            .thenReturn(Future.successful(updatedDeclaration))

          val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)
          val result = route(app, request).value

          status(result) mustBe ACCEPTED

          whenReady(result) {
            _ =>
              verify(repository, times(1)).get(chargeReference)
              verify(repository, times(1)).setState(chargeReference, State.Paid)
          }
        }
      }
    }

    "when a matching declaration is not found" - {

      "must return NOT_FOUND" in {

        val chargeReference = ChargeReference(1234567890)

        when(repository.get(chargeReference))
          .thenReturn(Future.successful(None))

        val request = FakeRequest(POST, routes.DeclarationController.update(chargeReference).url)
        val result = route(app, request).value

        status(result) mustBe NOT_FOUND

        whenReady(result) {
          _ =>
            verify(repository, times(1)).get(chargeReference)
        }
      }
    }
  }
}
