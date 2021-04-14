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
import play.api.libs.json.{JsObject, Json}
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

          val declaration = Declaration(chargeReference, State.PendingPayment, None,sentToEtmp = false, None,correlationId, journeyData, message)

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

        val declaration = Declaration(chargeReference, State.PendingPayment, None,sentToEtmp = false, None,correlationId, Json.obj(), Json.obj())

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

  "submit Amendment" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    val chargeReference = ChargeReference(1234567890)

    val userInformation = Json.obj(
      "firstName" -> "Harry",
      "lastName" -> "Potter",
      "identificationType" -> "passport",
      "identificationNumber" -> "SX12345",
      "emailAddress" -> "abc@gmail.com",
      "selectPlaceOfArrival" -> "LHR",
      "enterPlaceOfArrival" -> "Heathrow Airport",
      "dateOfArrival" -> "2018-05-31",
      "timeOfArrival" -> "13:20:00.000"
    )

    val journeyData: JsObject = Json.obj(
      "euCountryCheck" -> "greatBritain",
      "arrivingNICheck" -> true,
      "isUKResident" -> false,
      "bringingOverAllowance" -> true,
      "privateCraft" -> true,
      "ageOver17" -> true,
      "userInformation" -> userInformation,
      "purchasedProductInstances" -> Json.arr(
        Json.obj("path" -> "other-goods/adult/adult-clothing",
          "iid" -> "UCLFeP",
          "country" -> Json.obj(
            "code" -> "IN",
            "countryName" -> "title.india",
            "alphaTwoCode" -> "IN",
            "isEu" -> false,
            "isCountry" -> true,
            "countrySynonyms" -> Json.arr()
          ),
          "currency" -> "GBP",
          "cost" -> 500,
          "isVatPaid" -> false,
          "isCustomPaid" -> false,
          "isUccRelief" -> false)),
      "calculatorResponse" -> Json.obj(
        "calculation" -> Json.obj(
          "excise" -> "0.00",
          "customs" -> "12.50",
          "vat" -> "102.50",
          "allTax" -> "115.00"
        )
      ))

    val inputAmendmentData: JsObject = Json.obj(
      "journeyData" -> journeyData,
      "simpleDeclarationRequest" -> Json.obj(
        "requestCommon" -> Json.obj(
          "receiptDate" -> "2020-12-29T14:00:08Z",
          "requestParameters" -> Json.arr(
            Json.obj(
              "paramName" -> "REGIME",
              "paramValue" -> "PNGR"
            )
          ),
          "acknowledgementReference" -> (chargeReference.toString+"0")
        ),
        "requestDetail" -> Json.obj(
          "declarationAlcohol" -> Json.obj(
            "totalExciseAlcohol" -> "2.00",
            "totalCustomsAlcohol" -> "0.30",
            "totalVATAlcohol" -> "18.70",
            "declarationItemAlcohol" -> Json.arr(
              Json.obj(
                "commodityDescription" -> "Cider",
                "volume" -> "5",
                "goodsValue" -> "120.00",
                "valueCurrency" -> "USD",
                "valueCurrencyName" -> "USA dollars (USD)",
                "originCountry" -> "US",
                "originCountryName" -> "United States of America",
                "exchangeRate" -> "1.20",
                "exchangeRateDate" -> "2018-10-29",
                "goodsValueGBP" -> "91.23",
                "VATRESClaimed" -> false,
                "exciseGBP" -> "2.00",
                "customsGBP" -> "0.30",
                "vatGBP" -> "18.70"
              ),
              Json.obj(
                "commodityDescription" -> "Beer",
                "volume" -> "110",
                "goodsValue" -> "500.00",
                "valueCurrency" -> "GBP",
                "valueCurrencyName" -> "British pounds (GBP)",
                "originCountry" -> "FR",
                "originCountryName" -> "France",
                "exchangeRate" -> "1.00",
                "exchangeRateDate" -> "2020-12-29",
                "goodsValueGBP" -> "500.00",
                "VATRESClaimed" -> false,
                "exciseGBP" -> "88.00",
                "customsGBP" -> "0.00",
                "vatGBP" -> "117.60"
              )
            )
          ),
          "liabilityDetails" -> Json.obj(
            "totalExciseGBP" -> "102.54",
            "totalCustomsGBP" -> "534.89",
            "totalVATGBP" -> "725.03",
            "grandTotalGBP" -> "1362.46"
          ),
          "amendmentLiabilityDetails" -> Json.obj(
            "additionalExciseGBP" -> "88.00",
            "additionalCustomsGBP" -> "0.00",
            "additionalVATGBP" -> "117.60",
            "additionalTotalGBP" -> "205.60"
          ),
          "customerReference" -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
          "personalDetails" -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
          "declarationTobacco" -> Json.obj(
            "totalExciseTobacco" -> "100.54",
            "totalCustomsTobacco" -> "192.94",
            "totalVATTobacco" -> "149.92",
            "declarationItemTobacco" -> Json.arr(
              Json.obj(
                "commodityDescription" -> "Cigarettes",
                "quantity" -> "250",
                "goodsValue" -> "400.00",
                "valueCurrency" -> "USD",
                "valueCurrencyName" -> "USA dollars (USD)",
                "originCountry" -> "US",
                "originCountryName" -> "United States of America",
                "exchangeRate" -> "1.20",
                "exchangeRateDate" -> "2018-10-29",
                "goodsValueGBP" -> "304.11",
                "VATRESClaimed" -> false,
                "exciseGBP" -> "74.00",
                "customsGBP" -> "79.06",
                "vatGBP" -> "91.43"
              )
            )
          ),
          "declarationHeader" -> Json.obj("travellingFrom" -> "NON_EU Only", "expectedDateOfArrival" -> "2018-05-31", "ukVATPaid" -> false, "uccRelief" -> false, "portOfEntryName" -> "Heathrow Airport", "ukExcisePaid" -> false, "chargeReference" -> chargeReference.toString, "portOfEntry" -> "LHR", "timeOfEntry" -> "13:20", "onwardTravelGBNI" -> "GB", "messageTypes" -> Json.obj("messageType" -> "DeclarationAmend")),
          "contactDetails" -> Json.obj("emailAddress" -> "abc@gmail.com"),
          "declarationOther" -> Json.obj(
            "totalExciseOther" -> "0.00",
            "totalCustomsOther" -> "341.65",
            "totalVATOther" -> "556.41",
            "declarationItemOther" -> Json.arr(
              Json.obj(
                "commodityDescription" -> "Television",
                "quantity" -> "1",
                "goodsValue" -> "1500.00",
                "valueCurrency" -> "USD",
                "valueCurrencyName" -> "USA dollars (USD)",
                "originCountry" -> "US",
                "originCountryName" -> "United States of America",
                "exchangeRate" -> "1.20",
                "exchangeRateDate" -> "2018-10-29",
                "goodsValueGBP" -> "1140.42",
                "VATRESClaimed" -> false,
                "exciseGBP" -> "0.00",
                "customsGBP" -> "159.65",
                "vatGBP" -> "260.01"
              )
            )
          )
        )
      )
    )

    "when given a valid request" - {

      "and mongo is available" - {

        "must an updated Declaration with Amendment" in {

          val data = Json.obj("simpleDeclarationRequest" -> Json.obj("foo" -> "bar"))

          val amendData = inputAmendmentData - "journeyData"
          val declaration = Declaration(chargeReference, State.PendingPayment, Some(State.PendingPayment), sentToEtmp = false, amendSentToEtmp = Some(false), correlationId, journeyData, data, Some(amendData))

          val request = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
            .withJsonBody(inputAmendmentData).withHeaders("X-Correlation-ID" -> correlationId)

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.insertAmendment(inputAmendmentData, correlationId, chargeReference))
            .thenReturn(Future.successful(declaration))

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          headers(result) must contain ("X-Correlation-ID" -> correlationId)
          contentAsJson(result) mustBe declaration.amendData.get

          whenReady(result) {
            _ =>
              verify(declarationsRepository, times(1)).insertAmendment(inputAmendmentData, correlationId, chargeReference)
              verify(lockRepository, times(1)).release(1234567890)
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

        val declaration = Declaration(chargeReference, State.PendingPayment, Some(State.PendingPayment), sentToEtmp = false, amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), Some(Json.obj()))

        val request = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
          .withJsonBody(requestBody)

        when(declarationsRepository.insertAmendment(requestBody, correlationId, chargeReference))
          .thenReturn(Future.successful(declaration))

        val result = route(app, request).value

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustEqual Json.obj(
          "errors" -> Seq("Missing X-Correlation-ID header")
        )
      }

      "must return BAD_REQUEST with errors" in {

        val requestBody = Json.obj()

        val request = FakeRequest(POST, routes.DeclarationController.submitAmendment().url)
          .withJsonBody(requestBody).withHeaders("X-Correlation-ID" -> correlationId)

        val result = route(app, request).value

        status(result) mustBe BAD_REQUEST
        headers(result) must contain ("X-Correlation-ID" -> correlationId)
        contentAsJson(result).toString must include("object has too few properties (found 0 but schema requires at least 1)")
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

  "update amendment state" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Successful")

    "when a matching amendment is found" - {

      "and the amendment is in a Failed state" - {

        "must return CONFLICT" in {

          val chargeReference = ChargeReference(1234567890)

          val amendment = Declaration(chargeReference, State.Paid, amendState = Some(State.SubmissionFailed), sentToEtmp = false,
            amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), amendData = Some(Json.obj()))

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(amendment)))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

          val result = route(app, request).value

          status(result) mustBe CONFLICT
          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and the amendment is in a Paid state" - {

        "must return ACCEPTED without modifying the amendment" in {

          val chargeReference = ChargeReference(1234567890)

          val amendment = Declaration(chargeReference, State.Paid, amendState = Some(State.Paid), sentToEtmp = false,
            amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), amendData = Some(Json.obj()))

          when(lockRepository.lock(1234567890))
            .thenReturn(Future.successful(true))
          when(lockRepository.release(1234567890))
            .thenReturn(Future.successful(()))
          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(amendment)))

          val request = FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(jsonPayload)

          val result = route(app, request).value

          status(result) mustBe ACCEPTED
          verify(declarationsRepository, never()).setAmendState(eqTo(chargeReference), any())
          verify(lockRepository, times(1)).release(1234567890)
        }
      }

      "and updating its amendState fails" - {

        "must throw an exception" in {

          val chargeReference = ChargeReference(1234567890)

          val amendment = Declaration(chargeReference, State.Paid, amendState = Some(State.PendingPayment), sentToEtmp = false,
            amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), amendData = Some(Json.obj()))

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(amendment)))
          when(declarationsRepository.setAmendState(chargeReference, State.Paid))
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

      "and the payload status is Successful and updating its amendState succeeds" - {

        "must return ACCEPTED and update the amendState to Paid" in {

          val chargeReference = ChargeReference(1234567890)

          val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Successful")

          val amendment = Declaration(chargeReference, State.Paid, amendState = Some(State.PendingPayment), sentToEtmp = false,
            amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), amendData = Some(Json.obj()))
          val updatedAmendment = amendment copy (amendState = Some(State.Paid))

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(amendment)))
          when(declarationsRepository.setAmendState(chargeReference, State.Paid))
            .thenReturn(Future.successful(updatedAmendment))
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
              verify(declarationsRepository, times(1)).setAmendState(chargeReference, State.Paid)
              verify(lockRepository, times(1)).release(1234567890)
          }
        }
      }

      "and the payload status is Failed and updating its amendState succeeds" - {

        "must return ACCEPTED and update the amendState to PaymentFailed" in {

          val chargeReference = ChargeReference(1234567890)

          val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Failed")

          val amendment = Declaration(chargeReference, State.Paid, amendState = Some(State.PendingPayment), sentToEtmp = false,
            amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), amendData = Some(Json.obj()))
          val updatedAmendment = amendment copy (amendState = Some(State.PaymentFailed))

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(amendment)))
          when(declarationsRepository.setAmendState(chargeReference, State.PaymentFailed))
            .thenReturn(Future.successful(updatedAmendment))
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
              verify(declarationsRepository, times(1)).setAmendState(chargeReference, State.PaymentFailed)
              verify(lockRepository, times(1)).release(1234567890)
          }
        }
      }

      "and the payload status is Cancelled and updating its amendState succeeds" - {

        "must return ACCEPTED and update the amendState to PaymentCancelled" in {

          val chargeReference = ChargeReference(1234567890)

          val jsonPayload = Json.obj("reference" -> ChargeReference(1234567890), "status" -> "Cancelled")

          val amendment = Declaration(chargeReference, State.Paid, amendState = Some(State.PendingPayment), sentToEtmp = false,
            amendSentToEtmp = Some(false), correlationId, Json.obj(), Json.obj(), amendData = Some(Json.obj()))
          val updatedAmendment = amendment copy (amendState = Some(State.PaymentCancelled))

          when(declarationsRepository.get(chargeReference))
            .thenReturn(Future.successful(Some(amendment)))
          when(declarationsRepository.setAmendState(chargeReference, State.PaymentCancelled))
            .thenReturn(Future.successful(updatedAmendment))
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
              verify(declarationsRepository, times(1)).setAmendState(chargeReference, State.PaymentCancelled)
              verify(lockRepository, times(1)).release(1234567890)
          }
        }
      }
    }
  }

  "retrieve" - {

    "when given a valid request" - {

      "and mongo is available" - {

        "must return OK and a matched Declaration" in {

          val input = PreviousDeclarationRequest("POTTER", "SX12345", "1234567890")

          val declarationResponse = DeclarationResponse("greatBritain", arrivingNI = false, isOver17 = true, isUKResident = Some(true), isPrivateTravel = false, Json.obj("userInformation" -> "someUserInformation"), Json.obj("calculation" -> "somecalcultaion"), Json.obj("liabilityDetails" -> "SomeLiability"), Json.arr("oldPurchaseProductInstances" -> Json.obj()), amendCount = 0)

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

        val declarationResponse = DeclarationResponse("greatBritain", arrivingNI = false, isOver17 = true, isUKResident = Some(true), isPrivateTravel = false, Json.obj("userInformation" -> "someUserInformation"), Json.obj("calculation" -> "somecalcultaion"), Json.obj("liabilityDetails" -> "SomeLiability"), Json.arr("oldPurchaseProductInstances" -> Json.obj()), amendCount = 0)

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
