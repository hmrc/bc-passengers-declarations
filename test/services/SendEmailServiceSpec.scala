/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package services

import connectors.SendEmailConnector
import helpers.BaseSpec
import models._
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.DeclarationsRepository
import uk.gov.hmrc.http.HeaderCarrier


class SendEmailServiceSpec extends BaseSpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/test-path")

  override def beforeEach() {
    resetMocks()
  }

  val mockSendEmailConnector: SendEmailConnector = mock[SendEmailConnector]

  def resetMocks(): Unit = {
    reset(mockSendEmailConnector)

  }

  trait Setup {

    val emailService = new SendEmailService {
      val emailConnector: SendEmailConnector = mockSendEmailConnector
      val repository: DeclarationsRepository = mock[DeclarationsRepository]
      val testParams =  Map("TIMEOFARRIVAL" -> "12:16 PM",
        "COSTGBP_2" -> "12.50",
        "NAME" -> "John Doe",
        "NAME_2" -> "Fender Guitar",
        "DATEOFARRIVAL" -> "10 November 2020",
        "COSTGBP_1" -> "11.50",
        "COSTGBP_0" -> "10.50",
        "REFERENCE" -> "XAPR9876548240",
        "TRANSACTIONREFERENCE" -> "XAPR9876548240",
        "DATE" -> "9 November 2020 16:28",
        "NAME_1" -> "All other electronic devices",
        "TOTAL" -> "£2,000.99",
        "PLACEOFARRIVAL" -> "Heathrow",
        "NAME_0" -> "15 litres spirits")
    }
  }

  "generateEmailRequest" should {

    val testEmail = "myTestEmail@test.test"
    val testParams =  Map("TIMEOFARRIVAL" -> "12:16 PM",
      "COSTGBP_2" -> "12.50",
      "NAME" -> "John Doe",
      "NAME_2" -> "Fender Guitar",
      "DATEOFARRIVAL" -> "10 November 2020",
      "COSTGBP_1" -> "11.50",
      "COSTGBP_0" -> "10.50",
      "REFERENCE" -> "XAPR9876548240",
      "TRANSACTIONREFERENCE" -> "XAPR9876548240",
      "DATE" -> "9 November 2020 16:28",
      "NAME_1" -> "All other electronic devices",
      "TOTAL" -> "£2,000.99",
      "PLACEOFARRIVAL" -> "Heathrow",
      "NAME_0" -> "15 litres spirits")

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


  "Generating an email request" should {
    "construct the correct JSON" in new Setup {
      val result: SendEmailRequest = emailService.generateEmailRequest(Seq("test@email.com"),emailService.testParams)

      val resultAsJson: JsValue = Json.toJson(result)

      val expectedJson: JsValue = Json.parse {
        s"""
           |{
           |  "to":["test@email.com"],
           |  "templateId":"passengers_payment_confirmation",
           |  "parameters":{"TIMEOFARRIVAL" : "12:16 PM",
           |      "COSTGBP_2" : "12.50",
           |      "NAME" : "John Doe",
           |      "NAME_2" : "Fender Guitar",
           |      "DATEOFARRIVAL" : "10 November 2020",
           |      "COSTGBP_1" : "11.50",
           |      "COSTGBP_0" : "10.50",
           |      "REFERENCE" : "XAPR9876548240",
           |      "TRANSACTIONREFERENCE" : "XAPR9876548240",
           |      "DATE" : "9 November 2020 16:28",
           |      "NAME_1" : "All other electronic devices",
           |      "TOTAL" : "£2,000.99",
           |      "PLACEOFARRIVAL" : "Heathrow",
           |      "NAME_0" : "15 litres spirits"},
           |  "force":true
           |}
         """.stripMargin
      }
      resultAsJson shouldBe expectedJson
    }
  }
}
