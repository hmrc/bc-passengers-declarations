/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package services


import connectors.SendEmailConnector
import helpers.BaseSpec
import models._
import models.declarations.{Declaration, State}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Application
import play.api.http.Status.ACCEPTED
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.DeclarationsRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future


class SendEmailServiceSpec extends BaseSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/test-path")
  lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[HttpClient].toInstance(mock[HttpClient]))
    .build()

  override def beforeEach() {
    resetMocks()
  }

  private val mockSendEmailConnector: SendEmailConnector = new SendEmailConnector {
    override val sendEmailURL = "testSendEmailURL"
    override val http: HttpClient = mockWSHttp

    when(mockWSHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
      .thenReturn(Future.successful(HttpResponse(ACCEPTED)))

  }
  private val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  private val declarationsRepository = mock[DeclarationsRepository]

  def resetMocks(): Unit = {
    reset(declarationsRepository)

  }

  trait Setup {

    val emailService = new SendEmailService {
      val emailConnector: SendEmailConnector = mockSendEmailConnector
      val repository: DeclarationsRepository = declarationsRepository
      val servicesConfig: ServicesConfig = mockServicesConfig
      val testEmail = "testEmail@digital.hmrc.gov.uk"
      val chargeReference:ChargeReference = ChargeReference(1234567890)
      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
      val data: String =
        """{
          |        "simpleDeclarationRequest" : {
          |            "requestCommon" : {
          |                "receiptDate" : "2020-11-10T01:36:43Z",
          |                "requestParameters" : [
          |                    {
          |                        "paramName" : "REGIME",
          |                        "paramValue" : "PNGR"
          |                    }
          |                ],
          |                "acknowledgementReference" : "XAPR0000001074"
          |            },
          |            "requestDetail" : {
          |                "declarationAlcohol" : {
          |                    "totalExciseAlcohol" : "28.00",
          |                    "totalCustomsAlcohol" : "0.00",
          |                    "totalVATAlcohol" : "494.81",
          |                    "declarationItemAlcohol" : [
          |                        {
          |                            "commodityDescription" : "Beer",
          |                            "volume" : "35",
          |                            "goodsValue" : "3254.00",
          |                            "valueCurrency" : "USD",
          |                            "originCountry" : "BQ",
          |                            "exchangeRate" : "1.3303",
          |                            "exchangeRateDate" : "2020-12-07",
          |                            "goodsValueGBP" : "2446.06",
          |                            "VATRESClaimed" : false,
          |                            "exciseGBP" : "28.00",
          |                            "customsGBP" : "0.00",
          |                            "vatGBP" : "494.81"
          |                        }
          |                    ]
          |                },
          |                "liabilityDetails" : {
          |                    "totalExciseGBP" : "1000.00",
          |                    "totalCustomsGBP" : "1000.00",
          |                    "totalVATGBP" : "1000.00",
          |                    "grandTotalGBP" : "3000.00"
          |                },
          |                "customerReference" : {
          |                    "idType" : "passport",
          |                    "idValue" : "H4564654645",
          |                    "ukResident" : false
          |                },
          |                "personalDetails" : {
          |                    "firstName" : "John",
          |                    "lastName" : "Doe"
          |                },
          |                "declarationTobacco" : {
          |                    "totalExciseTobacco" : "108.96",
          |                    "totalCustomsTobacco" : "283.01",
          |                    "totalVATTobacco" : "191.60",
          |                    "declarationItemTobacco" : [
          |                        {
          |                            "commodityDescription" : "Cigarettes",
          |                            "quantity" : "357",
          |                            "goodsValue" : "753.00",
          |                            "valueCurrency" : "USD",
          |                            "originCountry" : "BQ",
          |                            "exchangeRate" : "1.3303",
          |                            "exchangeRateDate" : "2020-12-07",
          |                            "goodsValueGBP" : "566.03",
          |                            "VATRESClaimed" : false,
          |                            "exciseGBP" : "108.96",
          |                            "customsGBP" : "283.01",
          |                            "vatGBP" : "191.60"
          |                        }
          |                    ]
          |                },
          |                "declarationHeader" : {
          |                    "travellingFrom" : "NON_EU Only",
          |                    "expectedDateOfArrival" : "2020-11-10",
          |                    "ukVATPaid" : false,
          |                    "uccRelief" : false,
          |                    "ukExcisePaid" : false,
          |                    "chargeReference" : "XAPR0000001074",
          |                    "portOfEntry" : "LHR",
          |                    "timeOfEntry" : "12:16",
          |                    "onwardTravelGBNI" : "NI",
          |                    "messageTypes" : {
          |                        "messageType" : "DeclarationCreate"
          |                    }
          |                },
          |                "contactDetails" : {
          |                    "emailAddress" : "testEmail@digital.hmrc.gov.uk"
          |                },
          |                "declarationOther" : {
          |                    "totalExciseOther" : "0.00",
          |                    "totalCustomsOther" : "0.00",
          |                    "totalVATOther" : "0.00",
          |                    "declarationItemOther" : [
          |                        {
          |                            "commodityDescription" : "Adult clothing",
          |                            "quantity" : "1",
          |                            "goodsValue" : "258.00",
          |                            "valueCurrency" : "USD",
          |                            "originCountry" : "BQ",
          |                            "exchangeRate" : "1.3303",
          |                            "exchangeRateDate" : "2020-12-07",
          |                            "goodsValueGBP" : "193.94",
          |                            "VATRESClaimed" : false,
          |                            "exciseGBP" : "0.00",
          |                            "customsGBP" : "0.00",
          |                            "vatGBP" : "0.00"
          |                        }
          |                    ]
          |                }
          |            }
          |    },
          |    "lastUpdated" : "2020-12-07T01:37:30.832"
          |}""".stripMargin

      val declaration:Declaration = Declaration(chargeReference, State.PendingPayment, correlationId, Json.parse(data).as[JsObject])
      val bfEmail: String = "borderforce@digital.hmrc.gov.uk"
      val isleOfManEmail: String = "isleofman@digital.hmrc.gov.uk"

      when(declarationsRepository.get(chargeReference))
        .thenReturn(Future.successful(Some(declaration)))

      when(servicesConfig.getString("microservice.services.email.addressFirst"))
        .thenReturn(bfEmail)
      when(servicesConfig.getString("microservice.services.email.addressSecond"))
        .thenReturn(isleOfManEmail)

      val testParams =  Map(
        "subject" -> "Receipt for payment on goods brought into the UK - Reference number XAPR0000001074",
        "NAME" -> "John Doe",
        "DATE" -> "10 November 2020",
        "PLACEOFARRIVAL" -> "LHR",
        "DATEOFARRIVAL" -> "10 November 2020",
        "TIMEOFARRIVAL" -> "12:16 PM",
        "REFERENCE" -> "XAPR0000001074",
        "TOTAL" -> "£3,000.00",
        "TOTALEXCISEGBP" -> "£1,000.00",
        "TOTALCUSTOMSGBP" -> "£1,000.00",
        "TOTALVATGBP" -> "£1,000.00",
        "AllITEMS" -> "")

    }
    val zeroPoundsData: String =
      """{
        |        "simpleDeclarationRequest" : {
        |            "requestCommon" : {
        |                "receiptDate" : "2020-11-10T01:36:43Z",
        |                "requestParameters" : [
        |                    {
        |                        "paramName" : "REGIME",
        |                        "paramValue" : "PNGR"
        |                    }
        |                ],
        |                "acknowledgementReference" : "XAPR0000001074"
        |            },
        |            "requestDetail" : {
        |                "declarationAlcohol" : {
        |                    "totalExciseAlcohol" : "0.00",
        |                    "totalCustomsAlcohol" : "0.00",
        |                    "totalVATAlcohol" : "0.00",
        |                    "declarationItemAlcohol" : [
        |                        {
        |                            "commodityDescription" : "Beer",
        |                            "volume" : "35",
        |                            "goodsValue" : "3254.00",
        |                            "valueCurrency" : "USD",
        |                            "originCountry" : "BQ",
        |                            "exchangeRate" : "1.3303",
        |                            "exchangeRateDate" : "2020-12-07",
        |                            "goodsValueGBP" : "2446.06",
        |                            "VATRESClaimed" : false,
        |                            "exciseGBP" : "28.00",
        |                            "customsGBP" : "0.00",
        |                            "vatGBP" : "494.81"
        |                        }
        |                    ]
        |                },
        |                "liabilityDetails" : {
        |                    "totalExciseGBP" : "0.00",
        |                    "totalCustomsGBP" : "0.00",
        |                    "totalVATGBP" : "0.00",
        |                    "grandTotalGBP" : "0.00"
        |                },
        |                "customerReference" : {
        |                    "idType" : "passport",
        |                    "idValue" : "H4564654645",
        |                    "ukResident" : false
        |                },
        |                "personalDetails" : {
        |                    "firstName" : "John",
        |                    "lastName" : "Doe"
        |                },
        |                "declarationTobacco" : {
        |                    "totalExciseTobacco" : "108.96",
        |                    "totalCustomsTobacco" : "283.01",
        |                    "totalVATTobacco" : "191.60",
        |                    "declarationItemTobacco" : [
        |                        {
        |                            "commodityDescription" : "Cigarettes",
        |                            "quantity" : "357",
        |                            "goodsValue" : "753.00",
        |                            "valueCurrency" : "USD",
        |                            "originCountry" : "BQ",
        |                            "exchangeRate" : "1.3303",
        |                            "exchangeRateDate" : "2020-12-07",
        |                            "goodsValueGBP" : "566.03",
        |                            "VATRESClaimed" : false,
        |                            "exciseGBP" : "108.96",
        |                            "customsGBP" : "283.01",
        |                            "vatGBP" : "191.60"
        |                        }
        |                    ]
        |                },
        |                "declarationHeader" : {
        |                    "travellingFrom" : "NON_EU Only",
        |                    "expectedDateOfArrival" : "2020-11-10",
        |                    "ukVATPaid" : false,
        |                    "uccRelief" : false,
        |                    "ukExcisePaid" : false,
        |                    "chargeReference" : "XAPR0000001074",
        |                    "portOfEntry" : "LHR",
        |                    "timeOfEntry" : "12:16",
        |                    "onwardTravelGBNI" : "NI",
        |                    "messageTypes" : {
        |                        "messageType" : "DeclarationCreate"
        |                    }
        |                },
        |                "contactDetails" : {
        |                    "emailAddress" : "testEmail@digital.hmrc.gov.uk"
        |                },
        |                "declarationOther" : {
        |                    "totalExciseOther" : "0.00",
        |                    "totalCustomsOther" : "0.00",
        |                    "totalVATOther" : "0.00",
        |                    "declarationItemOther" : [
        |                        {
        |                            "commodityDescription" : "Adult clothing",
        |                            "quantity" : "1",
        |                            "goodsValue" : "258.00",
        |                            "valueCurrency" : "USD",
        |                            "originCountry" : "BQ",
        |                            "exchangeRate" : "1.3303",
        |                            "exchangeRateDate" : "2020-12-07",
        |                            "goodsValueGBP" : "193.94",
        |                            "VATRESClaimed" : false,
        |                            "exciseGBP" : "0.00",
        |                            "customsGBP" : "0.00",
        |                            "vatGBP" : "0.00"
        |                        }
        |                    ]
        |                }
        |            }
        |    },
        |    "lastUpdated" : "2020-12-07T01:37:30.832"
        |}""".stripMargin
  }

  "generateEmailRequest" should {

    val testEmail = "testEmail@digital.hmrc.gov.uk"
    val testParams =  Map(
      "subject" -> "Receipt for payment on goods brought into the UK - Reference number XAPR0000001074",
      "NAME" -> "John Doe",
      "DATE" -> "10 November 2020",
      "PLACEOFARRIVAL" -> "LHR",
      "DATEOFARRIVAL" -> "10 November 2020",
      "TIMEOFARRIVAL" -> "12:16 PM",
      "REFERENCE" -> "XAPR0000001074",
      "TOTAL" -> "£3,000.00",
      "TOTALEXCISEGBP" -> "£1,000.00",
      "TOTALCUSTOMSGBP" -> "£1,000.00",
      "TOTALVATGBP" -> "£1,000.00",
      "AllITEMS" -> "")

    val testRequest = SendEmailRequest(
      to = Seq(testEmail),
      templateId = "passengers_payment_confirmation",
      parameters = testParams,
      force = true
    )

    "return a EmailRequest with the correct email " in new Setup {
      emailService.generateEmailRequest(Seq(testEmail),emailService.testParams) shouldBe testRequest
    }
  }

  "getDeclaration" should {
    "return a valid Declaration on a valid Charge Reference" in new Setup{
      val declarationResult:Declaration = emailService.getDeclaration(emailService.chargeReference).futureValue
      declarationResult shouldBe emailService.declaration
    }
  }

  "sendEmail" should {
    "Return true for a valid email and params" in new Setup{
      emailService.sendEmail(emailService.testEmail,emailService.testParams).futureValue shouldBe true
    }
  }

  "sendPassengerEmail" should {
    "Return true for a valid email and params" in new Setup{
      emailService.sendPassengerEmail(emailService.testEmail,emailService.testParams).futureValue shouldBe true
    }
  }

  "getEmailParamsFromData" should{
    "Return a map of emailId and parameters" in new Setup{
      val localTestParams =  Map(
        "subject" -> "Receipt for payment on goods brought into the UK - Reference number  XAPR0000001074",
        "NAME" -> "John Doe",
        "DATE" -> "10 November 2020",
        "PLACEOFARRIVAL" -> "LHR",
        "DATEOFARRIVAL" -> "10 November 2020",
        "TIMEOFARRIVAL" -> "12:16 PM",
        "REFERENCE" -> "XAPR0000001074",
        "TOTAL" -> "£3000.00",
        "TOTALEXCISEGBP" -> "£1000.00",
        "TOTALCUSTOMSGBP" -> "£1000.00",
        "TOTALVATGBP" -> "£1000.00",
        "AllITEMS" -> "[{\"commodityDescription\":\"Beer\",\"volume\":\"35\",\"goodsValue\":\"3254.00\",\"valueCurrency\":\"USD\",\"originCountry\":\"BQ\",\"exchangeRate\":\"1.3303\",\"exchangeRateDate\":\"2020-12-07\",\"goodsValueGBP\":\"2446.06\",\"VATRESClaimed\":false,\"exciseGBP\":\"28.00\",\"customsGBP\":\"0.00\",\"vatGBP\":\"494.81\"},{\"commodityDescription\":\"Cigarettes\",\"quantity\":\"357\",\"goodsValue\":\"753.00\",\"valueCurrency\":\"USD\",\"originCountry\":\"BQ\",\"exchangeRate\":\"1.3303\",\"exchangeRateDate\":\"2020-12-07\",\"goodsValueGBP\":\"566.03\",\"VATRESClaimed\":false,\"exciseGBP\":\"108.96\",\"customsGBP\":\"283.01\",\"vatGBP\":\"191.60\"},{\"commodityDescription\":\"Adult clothing\",\"quantity\":\"1\",\"goodsValue\":\"258.00\",\"valueCurrency\":\"USD\",\"originCountry\":\"BQ\",\"exchangeRate\":\"1.3303\",\"exchangeRateDate\":\"2020-12-07\",\"goodsValueGBP\":\"193.94\",\"VATRESClaimed\":false,\"exciseGBP\":\"0.00\",\"customsGBP\":\"0.00\",\"vatGBP\":\"0.00\"}]"
      )
      val emailParams = Map(emailService.testEmail->localTestParams)
      emailService.getEmailParamsFromData(Json.parse(emailService.data).as[JsObject]) shouldBe emailParams
    }
  }

  "Generating an email request" should {
    "construct the correct JSON" in new Setup {
      val result: SendEmailRequest = emailService.generateEmailRequest(Seq(emailService.testEmail),emailService.testParams)

      val resultAsJson: JsValue = Json.toJson(result)

      val expectedJson: JsValue = Json.parse {
        s"""
           |{
           |  "to":["testEmail@digital.hmrc.gov.uk"],
           |  "templateId":"passengers_payment_confirmation",
           |  "parameters":{
           |      "subject" : "Receipt for payment on goods brought into the UK - Reference number XAPR0000001074",
           |      "NAME" : "John Doe",
           |      "DATE" : "10 November 2020",
           |      "PLACEOFARRIVAL" : "LHR",
           |      "DATEOFARRIVAL" : "10 November 2020",
           |      "TIMEOFARRIVAL" : "12:16 PM",
           |      "REFERENCE" : "XAPR0000001074",
           |      "TOTAL" : "£3,000.00",
           |      "TOTALEXCISEGBP" : "£1,000.00",
           |      "TOTALCUSTOMSGBP" : "£1,000.00",
           |      "TOTALVATGBP" : "£1,000.00",
           |      "AllITEMS" : ""},
           |  "force":true
           |}
         """.stripMargin
      }
      resultAsJson shouldBe expectedJson
    }
  }

  "isZeroPound " should {
    "return true if a Zero Pound Journey" in new Setup{
      emailService.isZeroPound(Json.parse(zeroPoundsData).as[JsObject]) shouldBe true
    }
    "return false if not a Zero Pound Journey" in new Setup{
      emailService.isZeroPound(Json.parse(emailService.data).as[JsObject]) shouldBe false
    }
  }

  "constructAndSendEmail" should {
    "send an email for a zero pound journey when disable-zero-pound-email feature is false" in new Setup{
      val declaration: Declaration = Declaration(emailService.chargeReference, State.PendingPayment, emailService.correlationId, Json.parse(zeroPoundsData).as[JsObject])
      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(false)
      when(declarationsRepository.get(emailService.chargeReference)).thenReturn(Future.successful(Some(declaration)))
      emailService.constructAndSendEmail(emailService.chargeReference).futureValue shouldBe true
    }

    "not send an email for a zero pound journey when disable-zero-pound-email feature is true" in new Setup{
      val declaration: Declaration = Declaration(emailService.chargeReference, State.PendingPayment, emailService.correlationId, Json.parse(zeroPoundsData).as[JsObject])
      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(true)
      when(declarationsRepository.get(emailService.chargeReference)).thenReturn(Future.successful(Some(declaration)))
      emailService.constructAndSendEmail(emailService.chargeReference).futureValue shouldBe false
    }

    "send an email for a non zero pound journey when disable-zero-pound-email feature is true" in new Setup{
      val declaration: Declaration = Declaration(emailService.chargeReference, State.PendingPayment, emailService.correlationId, Json.parse(emailService.data).as[JsObject])
      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(true)
      when(declarationsRepository.get(emailService.chargeReference)).thenReturn(Future.successful(Some(declaration)))
      emailService.constructAndSendEmail(emailService.chargeReference).futureValue shouldBe true
    }

    "send an email for a non zero pound journey when disable-zero-pound-email feature is false" in new Setup{
      val declaration: Declaration = Declaration(emailService.chargeReference, State.PendingPayment, emailService.correlationId, Json.parse(emailService.data).as[JsObject])
      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(false)
      when(declarationsRepository.get(emailService.chargeReference)).thenReturn(Future.successful(Some(declaration)))
      emailService.constructAndSendEmail(emailService.chargeReference).futureValue shouldBe true
    }
  }
}