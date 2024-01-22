/*
 * Copyright 2024 HM Revenue & Customs
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

package services

import connectors.SendEmailConnector
import helpers.{BaseSpec, Constants}
import models._
import models.declarations.Declaration
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Application
import play.api.http.Status.ACCEPTED
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.DeclarationsRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendEmailServiceSpec extends BaseSpec {

  implicit val hc: HeaderCarrier                        = HeaderCarrier()
  implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/test-path")
  lazy val app: Application                             = GuiceApplicationBuilder()
    .overrides(bind[HttpClient].toInstance(mock[HttpClient]))
    .build()

  override def beforeEach(): Unit =
    resetMocks()

  private val mockSendEmailConnector: SendEmailConnector = new SendEmailConnector {
    override val sendEmailURL     = "testSendEmailURL"
    override val http: HttpClient = mock[HttpClient]

    when(http.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
      .thenReturn(Future.successful(HttpResponse.apply(ACCEPTED, "")))

  }
  private val mockServicesConfig: ServicesConfig         = mock[ServicesConfig]
  private val declarationsRepository                     = mock[DeclarationsRepository]

  def resetMocks(): Unit =
    reset(declarationsRepository)

  trait Setup extends Constants {

    val emailService: SendEmailService = new SendEmailService {
      val emailConnector: SendEmailConnector = mockSendEmailConnector
      val repository: DeclarationsRepository = declarationsRepository
      val servicesConfig: ServicesConfig     = mockServicesConfig

      val bfEmail: String        = "borderforce@digital.hmrc.gov.uk"
      val isleOfManEmail: String = "isleofman@digital.hmrc.gov.uk"

      when(declarationsRepository.get(chargeReference))
        .thenReturn(Future.successful(Some(declaration)))

      when(servicesConfig.getString("microservice.services.email.addressFirst"))
        .thenReturn(bfEmail)
      when(servicesConfig.getString("microservice.services.email.addressSecond"))
        .thenReturn(isleOfManEmail)

    }

    val allItems: String = extractCommodityDescriptions(List(declarationAlcohol, declarationTobacco, declarationOther))

    val testParams: Map[String, String] = Map(
      "subject"         -> s"Receipt for payment on goods brought into the UK - Reference number  ${chargeReference.toString}",
      "NAME"            -> s"${userInformation("firstName").as[String]} ${userInformation("lastName").as[String]}",
      "DATE"            -> "29 December 2020",
      "PLACEOFARRIVAL"  -> userInformation("enterPlaceOfArrival").as[String],
      "DATEOFARRIVAL"   -> "31 May 2018",
      "TIMEOFARRIVAL"   -> "01:20 PM",
      "REFERENCE"       -> s"${chargeReference.toString}",
      "TOTAL"           -> "£1362.46",
      "TOTALEXCISEGBP"  -> "£102.54",
      "TOTALCUSTOMSGBP" -> "£534.89",
      "TOTALVATGBP"     -> "£725.03",
      "TRAVELLINGFROM"  -> "NON_EU Only",
      "AllITEMS"        -> allItems
    )

  }

  "generateEmailRequest" must {
    "return a EmailRequest with the correct email " in new Setup {
      val testRequest: SendEmailRequest = SendEmailRequest(
        to = Seq(emailAddress),
        templateId = "passengers_payment_confirmation",
        parameters = testParams,
        force = true
      )

      emailService.generateEmailRequest(Seq(emailAddress), testParams) shouldBe testRequest
    }
  }

  "getDeclaration" must {
    "return a valid Declaration on a valid Charge Reference" in new Setup {
      val declarationResult: Declaration = emailService.getDeclaration(chargeReference).futureValue
      declarationResult shouldBe declaration
    }
  }

  "sendEmail" must {
    "return true for a valid email and params" in new Setup {
      emailService.sendEmail(emailAddress, testParams).futureValue shouldBe true
    }

    "return true for a blank email and valid params" in new Setup {
      emailService.sendEmail("", testParams).futureValue shouldBe true
    }
  }

  "sendPassengerEmail" must {
    "return true for a valid email and params" in new Setup {
      emailService.sendPassengerEmail(emailAddress, testParams).futureValue shouldBe true
    }
  }

  "getEmailParamsFromData" must {
    "return a map of emailId and parameters" in new Setup {
      val emailParams: Map[String, Map[String, String]] = Map(emailAddress -> testParams)
      emailService.getEmailParamsFromData(declarationData) shouldBe emailParams
    }

    "return a map of emailId and parameters where some data can be unavailable" in new Setup {

      val alteredParams: Map[String, String]      =
        testParams ++ Map("AllITEMS" -> "[]", "DATE" -> "", "DATEOFARRIVAL" -> "", "TIMEOFARRIVAL" -> "")

      val missingDateRequestCommon: JsObject      = requestCommon + ("receiptDate" -> JsString(""))
      val missingDatesDeclarationHeader: JsObject =
        declarationHeader + ("expectedDateOfArrival" -> JsString("")) + ("timeOfEntry" -> JsString(""))
      val missingItemsInRequestDetail: JsObject =
        requestDetail - "declarationAlcohol" - "declarationTobacco" - "declarationOther" + ("declarationHeader" -> missingDatesDeclarationHeader)

      val changedDeclarationData: JsObject =
        declarationData + ("simpleDeclarationRequest" -> Json.obj(
          "requestCommon" -> missingDateRequestCommon,
          "requestDetail" -> missingItemsInRequestDetail
        ))

      val emailParams: Map[String, Map[String, String]] = Map(emailAddress -> alteredParams)

      emailService.getEmailParamsFromData(changedDeclarationData) shouldBe emailParams
    }
  }

  "getEmailParamsFromAmendedData" must {
    "Return a map of emailId and parameters" in new Setup {
      val emailParams: Map[String, Map[String, String]] = Map(emailAddress -> testParams)

      emailService.getEmailParamsFromData(amendmentData) shouldBe emailParams
    }
  }

  "Generating an email request" must {
    "construct the correct JSON" in new Setup {
      val result: SendEmailRequest =
        emailService.generateEmailRequest(Seq(emailAddress), testParams)

      val resultAsJson: JsValue = Json.toJson(result)

      val expectedJson: JsValue = Json.parse {
        s"""
           |{
           |  "to":["$emailAddress"],
           |  "templateId":"passengers_payment_confirmation",
           |  "parameters":{
           |      "subject" : "Receipt for payment on goods brought into the UK - Reference number  ${chargeReference.toString}",
           |      "NAME" : "${userInformation("firstName").as[String]} ${userInformation("lastName").as[String]}",
           |      "DATE" : "29 December 2020",
           |      "PLACEOFARRIVAL" : ${userInformation("enterPlaceOfArrival")},
           |      "DATEOFARRIVAL" : "31 May 2018",
           |      "TIMEOFARRIVAL" : "01:20 PM",
           |      "REFERENCE" : "${chargeReference.toString}",
           |      "TOTAL" : "£1362.46",
           |      "TOTALEXCISEGBP" : "£102.54",
           |      "TOTALCUSTOMSGBP" : "£534.89",
           |      "TOTALVATGBP" : "£725.03",
           |      "TRAVELLINGFROM" : "NON_EU Only",
           |      "AllITEMS" : "${s"${allItems.replace("\"", "\\\"")}"}"},
           |  "force":true
           |}
         """.stripMargin
      }
      resultAsJson shouldBe expectedJson
    }
  }

  ".isZeroPound " must {
    "return true if a Zero Pound Journey" in new Setup {
      emailService.isZeroPound(zeroPoundsData) shouldBe true
    }

    "return false if not a Zero Pound Journey" in new Setup {
      emailService.isZeroPound(declarationData) shouldBe false
    }
  }

  ".constructAndSendEmail" must {
    "send an email for a zero pound journey when disable-zero-pound-email feature is false" in new Setup {
      val zeroPoundsDeclaration: Declaration = declaration.copy(data = zeroPoundsData)

      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(false)
      when(declarationsRepository.get(chargeReference)).thenReturn(Future.successful(Some(zeroPoundsDeclaration)))
      emailService.constructAndSendEmail(chargeReference).futureValue shouldBe true
    }

    "not send an email for a zero pound journey when disable-zero-pound-email feature is true" in new Setup {
      val zeroPoundsDeclaration: Declaration = declaration.copy(data = zeroPoundsData)

      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(true)
      when(declarationsRepository.get(chargeReference)).thenReturn(Future.successful(Some(zeroPoundsDeclaration)))
      emailService.constructAndSendEmail(chargeReference).futureValue shouldBe false
    }

    "send an email for a non zero pound journey when disable-zero-pound-email feature is true" in new Setup {
      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(true)
      when(declarationsRepository.get(chargeReference)).thenReturn(Future.successful(Some(declaration)))
      emailService.constructAndSendEmail(chargeReference).futureValue shouldBe true
    }

    "send an email for a non zero pound journey when disable-zero-pound-email feature is false" in new Setup {
      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(false)
      when(declarationsRepository.get(chargeReference)).thenReturn(Future.successful(Some(declaration)))
      emailService.constructAndSendEmail(chargeReference).futureValue shouldBe true
    }

    "throw an exception when an amendment email is to be sent with no provided amendment data" in new Setup {
      val amendmentWithNoData: Declaration = amendment.copy(amendData = None)

      when(mockServicesConfig.getBoolean("features.disable-zero-pound-email")).thenReturn(false)
      when(declarationsRepository.get(chargeReference)).thenReturn(Future.successful(Some(amendmentWithNoData)))

      val exception: Exception = intercept[Exception] {
        emailService.constructAndSendEmail(chargeReference).futureValue
      }

      exception.getCause.getMessage shouldBe "No Amendment data found"
    }
  }

  private def extractCommodityDescriptions(declarations: List[JsObject]): String = {
    val combined = declarations.collect {
      case obj if (obj \ "declarationItemAlcohol").isDefined =>
        (obj \ "declarationItemAlcohol").as[JsArray].value.map(_.as[JsObject]).toArray
      case obj if (obj \ "declarationItemTobacco").isDefined =>
        (obj \ "declarationItemTobacco").as[JsArray].value.map(_.as[JsObject]).toArray
      case obj if (obj \ "declarationItemOther").isDefined   =>
        (obj \ "declarationItemOther").as[JsArray].value.map(_.as[JsObject]).toArray
    }
    s"[${combined.map(_.mkString(",")).mkString(",")}]"
  }
}
