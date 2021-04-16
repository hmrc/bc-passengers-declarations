package repositories

import java.time.LocalDateTime

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus, PreviousDeclarationRequest}
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import reactivemongo.api.indexes.IndexType
import reactivemongo.play.json.collection.JSONCollection
import suite.FailOnUnindexedQueries

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

class DeclarationsRepositorySpec extends FreeSpec with MustMatchers with FailOnUnindexedQueries
  with ScalaFutures with IntegrationPatience with OptionValues with Inside with EitherValues {

  private lazy val builder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

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
    "amendmentCount" -> 0,
    "purchasedProductInstances" -> Json.arr(
      Json.obj("path" -> "other-goods/adult/adult-clothing",
        "iid" -> "UCLFeP",
        "country" -> Json.obj(
          "code" -> "IN",
          "countryName" -> "title.south_georgia_and_the_south_sandwich_islands",
          "alphaTwoCode" -> "GS",
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

  val inputData: JsObject = Json.obj(
    "journeyData" -> journeyData,
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> "2020-12-29T12:14:08Z",
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000000"
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
            )
          )
        ),
        "liabilityDetails" -> Json.obj(
          "totalExciseGBP" -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP" -> "725.03",
          "grandTotalGBP" -> "1362.46"
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
        "declarationHeader" -> Json.obj("travellingFrom" -> "NON_EU Only", "expectedDateOfArrival" -> "2018-05-31", "ukVATPaid" -> false, "uccRelief" -> false, "portOfEntryName" -> "Heathrow Airport", "ukExcisePaid" -> false, "chargeReference" -> "XMPR0000000000", "portOfEntry" -> "LHR", "timeOfEntry" -> "13:20", "onwardTravelGBNI" -> "GB", "messageTypes" -> Json.obj("messageType" -> "DeclarationCreate")),
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

  val actualData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> "2020-12-29T12:14:08Z",
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000000"
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
            )
          )
        ),
        "liabilityDetails" -> Json.obj(
          "totalExciseGBP" -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP" -> "725.03",
          "grandTotalGBP" -> "1362.46"
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
        "declarationHeader" -> Json.obj("travellingFrom" -> "NON_EU Only", "expectedDateOfArrival" -> "2018-05-31", "ukVATPaid" -> false, "uccRelief" -> false, "portOfEntryName" -> "Heathrow Airport", "ukExcisePaid" -> false, "chargeReference" -> "XMPR0000000000", "portOfEntry" -> "LHR", "timeOfEntry" -> "13:20", "onwardTravelGBNI" -> "GB", "messageTypes" -> Json.obj("messageType" -> "DeclarationCreate")),
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

  val actualAmendmentData: JsObject = Json.obj(
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> "2020-12-29T14:00:08Z",
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "PNGR"
          )
        ),
        "acknowledgementReference" -> "XMPR00000000001"
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
        "declarationHeader" -> Json.obj("travellingFrom" -> "NON_EU Only", "expectedDateOfArrival" -> "2018-05-31", "ukVATPaid" -> false, "uccRelief" -> false, "portOfEntryName" -> "Heathrow Airport", "ukExcisePaid" -> false, "chargeReference" -> "XMPR0000000000", "portOfEntry" -> "LHR", "timeOfEntry" -> "13:20", "onwardTravelGBNI" -> "GB", "messageTypes" -> Json.obj("messageType" -> "DeclarationAmend")),
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
        "acknowledgementReference" -> "XMPR00000000001"
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
        "declarationHeader" -> Json.obj("travellingFrom" -> "NON_EU Only", "expectedDateOfArrival" -> "2018-05-31", "ukVATPaid" -> false, "uccRelief" -> false, "portOfEntryName" -> "Heathrow Airport", "ukExcisePaid" -> false, "chargeReference" -> "XMPR0000000000", "portOfEntry" -> "LHR", "timeOfEntry" -> "13:20", "onwardTravelGBNI" -> "GB", "messageTypes" -> Json.obj("messageType" -> "DeclarationAmend")),
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

  "a declarations repository" - {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    "must insert and remove declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val document = repository.insert(inputData, correlationId,sentToEtmp = false).futureValue.right.value

        inside(document) {
          case Declaration(id, _, None, false, None, cid, jd, data, None, _) =>

            id mustEqual document.chargeReference
            cid mustEqual correlationId
            jd mustEqual journeyData
            data mustEqual actualData
        }

        repository.remove(document.chargeReference).futureValue
        repository.get(document.chargeReference).futureValue mustNot be(defined)
      }
    }

    "must update a declaration record with amendments and remove record" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarationDocument = repository.insert(inputData, correlationId, sentToEtmp = false).futureValue.right.value
        val amendmentDocument = repository.insertAmendment(inputAmendmentData, correlationId, declarationDocument.chargeReference).futureValue
        journeyData.deepMerge(Json.obj("amendmentCount" -> 1))

        inside(amendmentDocument) {
          case Declaration(id, _, amendState, false, amendSentToEtmp, cid, jd, data, amendData, _) =>

            id mustEqual amendmentDocument.chargeReference
            amendState mustBe Some(State.PendingPayment)
            amendSentToEtmp mustBe Some(false)
            cid mustEqual correlationId
            jd mustEqual journeyData
            data mustEqual actualData
            amendData mustEqual Some(actualAmendmentData)
        }

        repository.remove(amendmentDocument.chargeReference).futureValue
        repository.get(amendmentDocument.chargeReference).futureValue mustNot be(defined)
      }
    }

    "must ensure indices" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        started(app).futureValue

        val indices = database.flatMap {
          _.collection[JSONCollection]("declarations")
            .indexesManager.list()
        }.futureValue

        indices.find {
          index =>
            index.name.contains("declarations-last-updated-index") &&
              index.key == Seq("lastUpdated" -> IndexType.Ascending)
        } mustBe defined


        indices.find {
          index =>
            index.name.contains("declarations-state-index") &&
              index.key == Seq("state" -> IndexType.Ascending)
        } mustBe defined
      }
    }

    "must provide a stream of unpaid declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.configure("declarations.payment-no-response-timeout" -> "1 minute").build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(1), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(2), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(3), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(4), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now.minusMinutes(5)),
          Declaration(ChargeReference(5), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(6), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val staleDeclarations = repository.unpaidDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        staleDeclarations.size mustEqual 5
        staleDeclarations.map(_.chargeReference) must contain allOf (ChargeReference(0), ChargeReference(1), ChargeReference(2), ChargeReference(5), ChargeReference(6))
      }
    }

    "must set the state of a declaration" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declaration = repository.insert(inputData, correlationId,sentToEtmp = false).futureValue.right.value

        val updatedDeclaration = repository.setState(declaration.chargeReference, State.Paid).futureValue

        updatedDeclaration.state mustEqual State.Paid
      }
    }

    "must set the state of an amendment" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declaration = repository.insert(inputData, correlationId,sentToEtmp = false).futureValue.right.value
        val amendment = repository.insertAmendment(inputAmendmentData, correlationId, declaration.chargeReference).futureValue

        val updatedAmendment = repository.setAmendState(amendment.chargeReference, State.Paid).futureValue

        updatedAmendment.amendState.get mustEqual State.Paid
      }
    }

    "must provide a stream of paid declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(2), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(3), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(4), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(5), State.Paid, Some(State.Paid), sentToEtmp=false, Some(true), correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(6), State.Paid, Some(State.Paid), sentToEtmp=false, Some(false), correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val paidDeclarations = repository.paidDeclarationsForEtmp.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        paidDeclarations.map(_.chargeReference) must contain only (
          ChargeReference(1), ChargeReference(3), ChargeReference(4), ChargeReference(5), ChargeReference(6)
        )
      }
    }

    "must provide a stream of paid amendments" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(2), State.Paid, Some(State.SubmissionFailed), sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(3), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(4), State.Paid, Some(State.Paid), sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(5), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(6), State.Paid, Some(State.Paid), sentToEtmp=false, Some(false), correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val paidDeclarations = repository.paidAmendmentsForEtmp.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        paidDeclarations.map(_.chargeReference) must contain only (
          ChargeReference(1), ChargeReference(3), ChargeReference(5)
        )
      }
    }

    "must provide a declaration when a paid declaration or amendment is present for given chargeReference, lastName, identification number" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

      val resultCalculation = Json.obj("excise" -> "0.00", "customs" -> "12.50", "vat" -> "102.50", "allTax" -> "115.00")

      val resultLiabilityDetails = Json.obj("totalExciseGBP" -> "102.54","totalCustomsGBP" -> "534.89","totalVATGBP" -> "725.03","grandTotalGBP" -> "1362.46")

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.Paid), sentToEtmp=false, None, correlationId, journeyData, actualData, None, LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(2), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(3), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now),
          Declaration(ChargeReference(4), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), None, LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val paidDeclaration = repository.get(input).futureValue

        paidDeclaration.get.isUKResident.get mustBe false
        paidDeclaration.get.isPrivateTravel mustBe true
        paidDeclaration.get.calculation mustBe resultCalculation
        paidDeclaration.get.userInformation mustBe userInformation
        paidDeclaration.get.liabilityDetails mustBe resultLiabilityDetails
      }
    }

    "must not provide a declaration when unpaid declaration or amendment is present for given chargeReference, lastName, identification number" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp=false, None, correlationId, journeyData, actualData, Some(actualAmendmentData), LocalDateTime.now),
          Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj(), Some(actualAmendmentData), LocalDateTime.now)
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        repository.get(input).futureValue mustBe None
      }
    }

    "must provide a stream of submission-failed declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(1), State.Paid, None,sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(2), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(3), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj())
        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        val failedDeclarations = repository.failedDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

        failedDeclarations.size mustEqual 2
        failedDeclarations.map(_.chargeReference) must contain only (ChargeReference(0), ChargeReference(2))
      }
    }

    "must fail to insert invalid declarations" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val errors = repository.insert(Json.obj(), correlationId,sentToEtmp=false).futureValue.left.value

        errors must contain ("""object has missing required properties (["receiptDate","requestParameters"])""")
      }
    }

    "reads the correct number of declaration states" in {

      database.flatMap(_.drop()).futureValue

      val app = builder.build()

      running(app) {

        val repository = app.injector.instanceOf[DeclarationsRepository]

        started(app).futureValue

        val declarations = List(
          Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),

          Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(2), State.Paid, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),

          Declaration(ChargeReference(3), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(4), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(5), State.PendingPayment, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),

          Declaration(ChargeReference(6), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(7), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(8), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(9), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),

          Declaration(ChargeReference(10), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(11), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(12), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(13), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj()),
          Declaration(ChargeReference(14), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, Json.obj(), Json.obj())

        )

        database.flatMap {
          _.collection[JSONCollection]("declarations")
            .insert(ordered = true)
            .many(declarations)
        }.futureValue

        implicit val mat: Materializer = app.injector.instanceOf[Materializer]

        repository.metricsCount
          .runWith(Sink.collection[DeclarationsStatus, List[DeclarationsStatus]])
          .futureValue.head mustBe DeclarationsStatus(
            pendingPaymentCount = 3,
            paymentCompleteCount = 2,
            paymentFailedCount = 5,
            paymentCancelledCount = 4,
            failedSubmissionCount = 1
          )

      }
    }
  }
}
