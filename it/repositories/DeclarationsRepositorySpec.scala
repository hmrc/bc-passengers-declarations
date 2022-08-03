
package repositories

import java.time.{LocalDateTime, ZoneOffset}
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import helpers.IntegrationSpecCommonBase
import models.declarations.{Declaration, State}
import models.{ChargeReference, DeclarationsStatus, PreviousDeclarationRequest}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}

import play.api.Configuration
import services.{ChargeReferenceService,ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.test.Helpers._
import akka.stream.Materializer
import org.mongodb.scala.Document
import org.scalatest.Inside.inside
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper

class DeclarationsRepositorySpec extends IntegrationSpecCommonBase with DefaultPlayMongoRepositorySupport[Declaration] {

  val validationService: ValidationService = app.injector.instanceOf[ValidationService]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  val chargeReferenceService: ChargeReferenceService = app.injector.instanceOf[ChargeReferenceService]

  override def repository = new DefaultDeclarationsRepository(mongoComponent,
    chargeReferenceService,
    validationService,
    Configuration(ConfigFactory.load(System.getProperty("config.resource")))
    )


  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    await(repository.collection.drop().toFuture())
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.collection.drop().toFuture())
  }


   lazy val builder: GuiceApplicationBuilder =
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
    ),
    "deltaCalculation" -> Some(Json.obj(
      "excise" -> "10.00",
      "customs" -> "10.50",
      "vat" -> "10.50",
      "allTax" -> "31.00"))
  )

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


  "a declarations repository" should {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
    val amendCorrelationId = "fe28db96-d9db-4220-9e12-f2d267267c30"

    await(repository.collection.drop().toFuture())

    "must insert and remove declarations" in {

      await(repository.collection.drop().toFuture())

      val app = builder.build()

      running(app) {


        val document = repository.insert(inputData, correlationId,sentToEtmp = false).futureValue.right.get

        inside(document) {
          case Declaration(id, _, None, false, None, cid, None, jd, data, None, _) =>

            id mustEqual document.chargeReference
            cid mustEqual correlationId
            jd mustEqual journeyData
          //  data mustEqual actualData


            repository.remove(document.chargeReference).futureValue
            repository.get(document.chargeReference).futureValue mustNot be(defined)
        }
      }
    }

    "must update a declaration record with amendments and remove record" in {

      await(repository.collection.drop().toFuture())

      val app = builder.build()

      running(app) {


        val declarationDocument = repository.insert(inputData, correlationId, sentToEtmp = false).futureValue.right.get
        val amendmentDocument = repository.insertAmendment(inputAmendmentData, amendCorrelationId, declarationDocument.chargeReference).futureValue
        journeyData.deepMerge(Json.obj("amendmentCount" -> 1))

        inside(amendmentDocument) {
          case Declaration(id, _, amendState, false, amendSentToEtmp, _, correlationIdFromDataBase, jd, data, amendData, _) =>

            id mustEqual amendmentDocument.chargeReference
            amendState mustBe Some(State.PendingPayment)
            amendSentToEtmp mustBe Some(false)
            correlationIdFromDataBase.get mustEqual amendCorrelationId
            jd mustEqual journeyData
            //data mustEqual actualData
            amendData mustEqual Some(actualAmendmentData)
        }

        repository.remove(amendmentDocument.chargeReference).futureValue
        repository.get(amendmentDocument.chargeReference).futureValue mustNot be(defined)
      }
    }

        "must ensure indices" in {

          await(repository.collection.drop().toFuture())

          val app = builder.build()

          running(app) {

            val indices : Seq[Document] = await(repository.collection.listIndexes().toFuture())


            indices.map(
              doc =>
                doc.toJson.contains("lastUpdated") match {
                  case true => doc.toJson.contains("declarations-last-updated-index") mustEqual true
                  case false if doc.toJson.contains("state")  => doc.toJson.contains("declarations-state-index") mustEqual true
                  case _ => doc.toJson.contains("test-DeclarationsRepositorySpec.declarations") mustEqual true
                }
            )

            indices.size mustBe 4

          }
        }


            "must provide a stream of unpaid declarations" in {

              await(repository.collection.drop().toFuture())

              val app = builder.configure("declarations.payment-no-response-timeout" -> "1 minute").build()

              running(app) {


                val declarations = List(
                  Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                  Declaration(ChargeReference(1), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                  Declaration(ChargeReference(2), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                  Declaration(ChargeReference(3), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                  Declaration(ChargeReference(4), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                  Declaration(ChargeReference(5), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(6), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC))
                )

                await(repository.collection.insertMany(declarations).toFuture())


                implicit val mat: Materializer = app.injector.instanceOf[Materializer]

                val staleDeclarations = repository.unpaidDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

                staleDeclarations.size mustEqual 5
                staleDeclarations.map(_.chargeReference) must contain allOf (ChargeReference(0), ChargeReference(1), ChargeReference(2), ChargeReference(5), ChargeReference(6))
              }
            }

             "must provide a stream of unpaid amendments" in {

             await(repository.collection.drop().toFuture())

            val app = builder.configure("declarations.payment-no-response-timeout" -> "1 minute").build()

            running(app) {


              val declarations = List(
                Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                Declaration(ChargeReference(1), State.Paid, Some(State.PaymentFailed), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                Declaration(ChargeReference(2), State.Paid, Some(State.PaymentCancelled), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                Declaration(ChargeReference(3), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                Declaration(ChargeReference(4), State.Paid, Some(State.SubmissionFailed), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
                Declaration(ChargeReference(5), State.Paid, Some(State.PendingPayment), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC)),
                Declaration(ChargeReference(6), State.Paid, Some(State.PaymentFailed), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(Json.obj()), LocalDateTime.now(ZoneOffset.UTC))
              )


              await(repository.collection.insertMany(declarations).toFuture())


              val staleDeclarations = await(repository.unpaidAmendments.runWith(Sink.collection[Declaration, List[Declaration]]))

              staleDeclarations.size mustEqual 5
              staleDeclarations.map(_.chargeReference) must contain allOf (ChargeReference(0), ChargeReference(1), ChargeReference(2), ChargeReference(5), ChargeReference(6))
            }
          }

          "must set the state of a declaration" in {

               await(repository.collection.drop().toFuture())

               val app = builder.build()

               running(app) {

                 val declaration = await(repository.insert(inputData, "testId",sentToEtmp = false)).right.get

                 await(repository.setState(declaration.chargeReference, State.Paid))

                 val updatedDeclaration : Declaration = repository.get(declaration.chargeReference).futureValue.get

                 updatedDeclaration.state mustEqual State.Paid
               }
             }

          "must set the state of an amendment" in {

               await(repository.collection.drop().toFuture())

               val app = builder.build()

               running(app) {


                 val declaration = repository.insert(inputData, correlationId,sentToEtmp = false).futureValue.right.get
                 val amendment = repository.insertAmendment(inputAmendmentData, correlationId, declaration.chargeReference).futureValue

                 await(repository.setAmendState(amendment.chargeReference, State.Paid))

                 val updatedAmendment : Declaration = repository.get(declaration.chargeReference).futureValue.get

                 updatedAmendment.amendState.get mustEqual State.Paid
               }
             }

               "must provide a stream of paid declarations" in {

                 await(repository.collection.drop().toFuture())

                 val app = builder.build()

                 running(app) {


                   val declarations = List(
                     Declaration(ChargeReference(0), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(2), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(3), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(4), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(5), State.Paid, Some(State.Paid), sentToEtmp=false, Some(true), correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(6), State.Paid, Some(State.Paid), sentToEtmp=false, Some(false), correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC))
                   )


                   await(repository.collection.insertMany(declarations).toFuture())

                   implicit val mat: Materializer = app.injector.instanceOf[Materializer]

                   val paidDeclarations = repository.paidDeclarationsForEtmp.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

                   paidDeclarations.map(_.chargeReference) must contain only (
                     ChargeReference(1), ChargeReference(3), ChargeReference(4), ChargeReference(5), ChargeReference(6)
                   )
                 }
               }

        "must provide a stream of paid amendments" in {

          await(repository.collection.drop().toFuture())

              val app = builder.build()

              running(app) {



                val declarations = List(
                  Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp=false, None, correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(1), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(2), State.Paid, Some(State.SubmissionFailed), sentToEtmp=false, None, correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(3), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(4), State.Paid, Some(State.Paid), sentToEtmp=false, None, correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(5), State.Paid, Some(State.Paid), sentToEtmp=true, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                  Declaration(ChargeReference(6), State.Paid, Some(State.Paid), sentToEtmp=false, Some(false), correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC))
                )

                await(repository.collection.insertMany(declarations).toFuture())


                implicit val mat: Materializer = app.injector.instanceOf[Materializer]

                val paidDeclarations = repository.paidAmendmentsForEtmp.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

                paidDeclarations.map(_.chargeReference) must contain only (
                  ChargeReference(1), ChargeReference(3), ChargeReference(5)
                )
              }
            }

               "must provide a declaration when a paid declaration or amendment is present for given chargeReference, lastName, identification number" in {

                 await(repository.collection.drop().toFuture())

             val app = builder.build()

             val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

             val resultCalculation = Json.obj("excise" -> "0.00", "customs" -> "12.50", "vat" -> "102.50", "allTax" -> "115.00")

             val resultLiabilityDetails = Json.obj("totalExciseGBP" -> "102.54","totalCustomsGBP" -> "534.89","totalVATGBP" -> "725.03","grandTotalGBP" -> "1362.46")

             running(app) {



               val declarations = List(
                 Declaration(ChargeReference(0), State.Paid, Some(State.Paid), sentToEtmp=false, None, correlationId, None, journeyData, actualData, None, LocalDateTime.now(ZoneOffset.UTC)),
                 Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                 Declaration(ChargeReference(2), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                 Declaration(ChargeReference(3), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC)),
                 Declaration(ChargeReference(4), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj(), None, LocalDateTime.now(ZoneOffset.UTC))
               )

               await(repository.collection.insertMany(declarations).toFuture())

               implicit val mat: Materializer = app.injector.instanceOf[Materializer]

               val paidDeclaration = repository.get(input).futureValue

               paidDeclaration.get.isUKResident.get mustBe false
               paidDeclaration.get.isPrivateTravel mustBe true
               paidDeclaration.get.calculation mustBe resultCalculation
               paidDeclaration.get.userInformation mustBe userInformation
               paidDeclaration.get.liabilityDetails mustBe resultLiabilityDetails
             }
           }

             "must not provide a declaration when payment failed for declaration or amendment is present for given chargeReference, lastName, identification number" in {

               await(repository.collection.drop().toFuture())

               val app = builder.build()

                 val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

               running(app) {



                   val declarations = List(
                     Declaration(ChargeReference(0), State.Paid, Some(State.PaymentFailed), sentToEtmp=false, None, correlationId, Some(amendCorrelationId), journeyData, actualData, Some(actualAmendmentData), LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(actualAmendmentData), LocalDateTime.now(ZoneOffset.UTC))
                   )

                 await(repository.collection.insertMany(declarations).toFuture())

                 repository.get(input).futureValue mustBe None
               }
             }

             "must provide a declaration when paid declaration & pending payment amendment is present for given chargeReference, lastName" in {

               await(repository.collection.drop().toFuture())

               val deltaCalculation = Some(Json.obj("excise" -> "10.00", "customs" -> "10.50", "vat" -> "10.50", "allTax" -> "31.00"))

               val app = builder.build()

               val input = PreviousDeclarationRequest("POTTER", ChargeReference(0).toString)

               running(app) {


                   val declarations = List(
                     Declaration(ChargeReference(0), State.Paid, Some(State.PendingPayment), sentToEtmp=false, None, correlationId, Some(amendCorrelationId), journeyData, actualData, Some(actualAmendmentData), LocalDateTime.now(ZoneOffset.UTC)),
                     Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, Some(amendCorrelationId), Json.obj(), Json.obj(), Some(actualAmendmentData), LocalDateTime.now(ZoneOffset.UTC))
                   )

                 await(repository.collection.insertMany(declarations).toFuture())

                 val pendingAmendment = await(repository.get(input))


                 pendingAmendment.get.amendState.get mustBe "pending-payment"
                 pendingAmendment.get.deltaCalculation mustBe deltaCalculation
               }
             }

             "must provide a stream of submission-failed declarations" in {

               await(repository.collection.drop().toFuture())

               val app = builder.build()

               running(app) {


                 val declarations = List(
                   Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                   Declaration(ChargeReference(1), State.Paid, None,sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                   Declaration(ChargeReference(2), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                   Declaration(ChargeReference(3), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj())
                 )

                 await(repository.collection.insertMany(declarations).toFuture())

                 implicit val mat: Materializer = app.injector.instanceOf[Materializer]

                 val failedDeclarations = repository.failedDeclarations.runWith(Sink.collection[Declaration, List[Declaration]]).futureValue

                 failedDeclarations.size mustEqual 2
                 failedDeclarations.map(_.chargeReference) must contain only (ChargeReference(0), ChargeReference(2))
               }
             }

              "must fail to insert invalid declarations" in {

                await(repository.collection.drop().toFuture())

                  val app = builder.build()

                  running(app) {


                    val errors = repository.insert(Json.obj(), correlationId,sentToEtmp=false).futureValue.left.get

                    errors must contain ("""object has missing required properties (["receiptDate","requestParameters"])""")
                  }
                }

                    "reads the correct number of declaration states" in {

                    await(repository.collection.drop().toFuture())

                    val app = builder.build()

                    running(app) {



                      val declarations = List(
                        Declaration(ChargeReference(0), State.SubmissionFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),

                        Declaration(ChargeReference(1), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(2), State.Paid, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),

                        Declaration(ChargeReference(3), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(4), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(5), State.PendingPayment, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),

                        Declaration(ChargeReference(6), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(7), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(8), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(9), State.PaymentCancelled, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),

                        Declaration(ChargeReference(10), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(11), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(12), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(13), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj()),
                        Declaration(ChargeReference(14), State.PaymentFailed, None, sentToEtmp=false, None, correlationId, None, Json.obj(), Json.obj())

                      )

                      await(repository.collection.insertMany(declarations).toFuture())


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

