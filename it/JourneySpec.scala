/*

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.client.WireMock.{any => _, _}
import com.typesafe.config.ConfigFactory
import connectors.HODConnector
import controllers.routes
import helpers.IntegrationSpecCommonBase
import models.declarations.Declaration

import org.scalatest.time.{Minutes, Seconds, Span}
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.{DefaultDeclarationsRepository, DefaultLockRepository}
import services.{ChargeReferenceService, ValidationService}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.AuditingTools
import utils.WireMockHelper
import workers.DeclarationSubmissionWorker

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

class JourneySpec extends IntegrationSpecCommonBase with WireMockHelper with DefaultPlayMongoRepositorySupport[Declaration] {

  val validationService: ValidationService = app.injector.instanceOf[ValidationService]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  val chargeReferenceService: ChargeReferenceService = app.injector.instanceOf[ChargeReferenceService]

  override def repository = new DefaultDeclarationsRepository(mongoComponent,
    chargeReferenceService,
    validationService,
    Configuration(ConfigFactory.load(System.getProperty("config.resource")))
  )

  def lockRepository = new DefaultLockRepository(mongoComponent, Configuration(ConfigFactory.load(System.getProperty("config.resource"))))

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

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(2, Minutes)),
    interval = scaled(Span(10, Seconds))
  )

  lazy val builder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.des.port" -> server.port()
    )

  val data: JsObject = Json.obj(
    "journeyData" -> Json.obj("some" -> "journey data"),
    "simpleDeclarationRequest" -> Json.obj(
      "requestCommon" -> Json.obj(
        "receiptDate" -> "2020-12-29T12:14:08Z",
        "acknowledgementReference" -> "XJPR57685246250",
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "PNGR"
          )
        )
      ),
      "requestDetail" -> Json.obj(
        "customerReference" -> Json.obj("idType" -> "passport", "idValue" -> "SX12345", "ukResident" -> false),
        "personalDetails" -> Json.obj("firstName" -> "Harry", "lastName" -> "Potter"),
        "contactDetails" -> Json.obj("emailAddress" -> "abc@gmail.com"),
        "declarationHeader" -> Json.obj("chargeReference" -> "XJPR5768524625", "portOfEntry" -> "LHR", "portOfEntryName" -> "Heathrow Airport", "expectedDateOfArrival" -> "2018-05-31", "timeOfEntry" -> "13:20", "messageTypes" -> Json.obj("messageType" -> "DeclarationCreate"), "travellingFrom" -> "NON_EU Only", "onwardTravelGBNI" -> "GB", "uccRelief" -> false, "ukVATPaid" -> false, "ukExcisePaid" -> false),
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
        ),
        "liabilityDetails" -> Json.obj(
          "totalExciseGBP" -> "102.54",
          "totalCustomsGBP" -> "534.89",
          "totalVATGBP" -> "725.03",
          "grandTotalGBP" -> "1362.46"
        )
      )
    )
  )

  "a paid declaration must be submitted to des" in {

    val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"

    server.stubFor(post(urlPathEqualTo("/declarations/passengerdeclaration/v1"))
      .willReturn(aResponse().withStatus(NO_CONTENT))
    )

    await(repository.collection.drop().toFuture())

    val app = builder.build()

    running(app) {

      val response = route(app, FakeRequest(POST, routes.DeclarationController.submit().url)
        .withJsonBody(data).withHeaders("X-Correlation-ID" -> correlationId)).get

      val chargeReference: String = (contentAsJson(response) \ "simpleDeclarationRequest" \ "requestDetail" \ "declarationHeader" \ "chargeReference").as[JsString].value

      route(app, FakeRequest(POST, routes.DeclarationController.update().url).withJsonBody(
        Json.obj("reference" -> chargeReference, "status" -> "Successful")
      )).get.futureValue

      val hODConnector = app.injector.instanceOf[HODConnector]

      val auditConnector = app.injector.instanceOf[AuditConnector]
      val auditingTools =  app.injector.instanceOf[AuditingTools]

      val submissionWorker = new DeclarationSubmissionWorker(repository, lockRepository, hODConnector, Configuration(ConfigFactory.load(System.getProperty("config.resource"))), auditConnector, auditingTools)

    //  submissionWorker.tap.pull.futureValue.get

      server.verify(1, postRequestedFor(urlEqualTo("/declarations/passengerdeclaration/v1")))
    }
  }
}

*/
