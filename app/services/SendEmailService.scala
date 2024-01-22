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

import connectors.{EmailErrorResponse, SendEmailConnector, SendEmailConnectorImpl}
import models.declarations.Declaration
import models.{ChargeReference, SendEmailRequest}
import play.api.i18n.Lang.logger.logger
import play.api.libs.json._
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDate, LocalTime}
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SendEmailServiceImpl @Inject() (
  val emailConnector: SendEmailConnectorImpl,
  val repository: DefaultDeclarationsRepository,
  val servicesConfig: ServicesConfig
) extends SendEmailService

trait SendEmailService {

  val passengerTemplate = "passengers_payment_confirmation"
  val emailConnector: SendEmailConnector
  val repository: DeclarationsRepository
  val servicesConfig: ServicesConfig

  private[services] def generateEmailRequest(
    emailAddress: Seq[String],
    parameters: Map[String, String]
  ): SendEmailRequest =
    SendEmailRequest(
      to = emailAddress,
      templateId = passengerTemplate,
      parameters,
      force = true
    )

  private[services] def sendEmail(emailAddress: String, parameters: Map[String, String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean] = {
    val configuredEmailFirst: String  = servicesConfig.getConfString("email.addressFirst", "")
    val configuredEmailSecond: String = servicesConfig.getConfString("email.addressSecond", "")
    if (emailAddress.nonEmpty) {
      emailConnector.requestEmail(generateEmailRequest(Seq(emailAddress), parameters)).map { _ =>
        logger.info("[SendEmailServiceImpl] [sendEmail] Email sent for the passenger")
      }
    }
    emailConnector
      .requestEmail(generateEmailRequest(Seq(configuredEmailFirst, configuredEmailSecond), parameters))
      .map { result =>
        logger.info("[SendEmailServiceImpl] [sendEmail] Email sent for Border force/Isle of Man")
        result
      }
  }

  private[services] def isZeroPound(data: JsObject): Boolean =
    data.value
      .apply("simpleDeclarationRequest")
      .\("requestDetail")
      .\("liabilityDetails")
      .\("grandTotalGBP")
      .asOpt[String]
      .getOrElse("")
      .equalsIgnoreCase("0.00")

  private[services] def getDataOrAmendmentData(declaration: Declaration): JsObject =
    if (declaration.amendState.isDefined) {
      declaration.amendData.getOrElse(throw new Exception(s"No Amendment data found"))
    } else {
      declaration.data
    }

  private[services] def getEmailParamsFromData(data: JsObject): Map[String, Map[String, String]] = {
    val simpleDeclarationRequest: JsValue = data.value.apply("simpleDeclarationRequest")
    val requestCommon: JsLookupResult     = simpleDeclarationRequest.\("requestCommon")
    val requestDetail: JsLookupResult     = simpleDeclarationRequest.\("requestDetail")
    val personalDetails: JsLookupResult   = requestDetail.\("personalDetails")
    val declarationHeader: JsLookupResult = requestDetail.\("declarationHeader")
    val liabilityDetails: JsLookupResult  = requestDetail.\("liabilityDetails")
    val contactDetails: JsLookupResult    = requestDetail.\("contactDetails")

    val itemsAlcohol: JsArray    = requestDetail.\("declarationAlcohol") match {
      case JsDefined(value) => value.\("declarationItemAlcohol").as[JsArray]
      case _: JsUndefined   => Json.arr()
    }
    val itemsTobacco: JsArray    = requestDetail.\("declarationTobacco") match {
      case JsDefined(value) => value.\("declarationItemTobacco").as[JsArray]
      case _: JsUndefined   => Json.arr()
    }
    val itemsOtherGoods: JsArray = requestDetail.\("declarationOther") match {
      case JsDefined(value) => value.\("declarationItemOther").as[JsArray]
      case _: JsUndefined   => Json.arr()
    }
    val allItems: JsArray        = itemsAlcohol ++ itemsTobacco ++ itemsOtherGoods

    val emailId: String   = contactDetails.\("emailAddress").asOpt[String].getOrElse("")
    val firstName: String = personalDetails.\("firstName").asOpt[String].getOrElse("")
    val lastName: String  = personalDetails.\("lastName").asOpt[String].getOrElse("")
    val fullName          = s"$firstName $lastName"

    val receiptDate: String          = requestCommon.\("receiptDate").asOpt[String].getOrElse("")
    val receiptDateFormatted: String = Option(receiptDate)
      .filter(_.nonEmpty)
      .map { dateString =>
        LocalDate.parse(dateString.substring(0, 10)).format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
      }
      .getOrElse(receiptDate)

    val portOfEntry: String = declarationHeader.\("portOfEntryName").asOpt[String].getOrElse("")

    val expectedDateOfArrival: String = declarationHeader.\("expectedDateOfArrival").asOpt[String].getOrElse("")
    val expectedDateArr: String       = Option(expectedDateOfArrival)
      .filter(_.nonEmpty)
      .map(dateString => LocalDate.parse(dateString).format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
      .getOrElse(expectedDateOfArrival)

    val timeOfEntry: String          = declarationHeader.\("timeOfEntry").asOpt[String].getOrElse("")
    val formattedTimeOfEntry: String = Option(timeOfEntry)
      .filter(_.trim.nonEmpty)
      .map(timeString => LocalTime.parse(timeString).format(DateTimeFormatter.ofPattern("hh:mm a")).toUpperCase())
      .getOrElse(timeOfEntry)

    val chargeReference: String = declarationHeader.\("chargeReference").asOpt[String].getOrElse("")
    val travellingFrom: String  = declarationHeader.\("travellingFrom").asOpt[String].getOrElse("")

    val grandTotalGBP: String   = s"£${liabilityDetails.\("grandTotalGBP").asOpt[String].getOrElse("")}"
    val totalExciseGBP: String  = s"£${liabilityDetails.\("totalExciseGBP").asOpt[String].getOrElse("")}"
    val totalCustomsGBP: String = s"£${liabilityDetails.\("totalCustomsGBP").asOpt[String].getOrElse("")}"
    val totalVATGBP: String     = s"£${liabilityDetails.\("totalVATGBP").asOpt[String].getOrElse("")}"

    val staticSubjectNonZero = "Receipt for payment on goods brought into the UK - Reference number "
    val staticSubjectZero    = "Receipt for declaration of goods brought into Northern Ireland - Reference number "
    val dynamicSubject       = if (grandTotalGBP.equalsIgnoreCase("£0.00")) {
      staticSubjectZero
    } else {
      staticSubjectNonZero
    }
    val subject              = s"$dynamicSubject $chargeReference"

    val parameters: Map[String, String] = Map(
      "subject"         -> subject,
      "NAME"            -> fullName,
      "DATE"            -> receiptDateFormatted,
      "PLACEOFARRIVAL"  -> portOfEntry,
      "DATEOFARRIVAL"   -> expectedDateArr,
      "TIMEOFARRIVAL"   -> formattedTimeOfEntry,
      "REFERENCE"       -> chargeReference,
      "TOTAL"           -> grandTotalGBP,
      "TOTALEXCISEGBP"  -> totalExciseGBP,
      "TOTALCUSTOMSGBP" -> totalCustomsGBP,
      "TOTALVATGBP"     -> totalVATGBP,
      "TRAVELLINGFROM"  -> travellingFrom,
      "AllITEMS"        -> allItems.toString()
    )

    Map(emailId -> parameters)
  }

  def constructAndSendEmail(
    reference: ChargeReference
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val disableZeroPoundEmail = servicesConfig.getBoolean("features.disable-zero-pound-email")
    getDeclaration(reference).flatMap { x =>
      val data: JsObject = getDataOrAmendmentData(x)
      val emailParams    = getEmailParamsFromData(data)
      val emailId        = emailParams.keys.head
      val params         = emailParams.getOrElse(emailId, Map.empty)
      if (disableZeroPoundEmail && isZeroPound(data)) {
        logger.warn(
          "[SendEmailServiceImpl] [constructAndSendEmail] Email not sent as Zero Pound and Zero Pound Email is disabled"
        )
        Future.successful(false)
      } else {
        sendPassengerEmail(emailId, params)
      }
    }
  }

  private[services] def getDeclaration(
    reference: ChargeReference
  )(implicit ec: ExecutionContext): Future[Declaration] = {
    val fDec: Future[Option[Declaration]] = repository.get(reference)
    fDec.map(_.getOrElse(throw new Exception(s"Option is empty")))
  }

  private[services] def sendPassengerEmail(emailAddressAll: String, parameters: Map[String, String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean] =
    sendEmail(emailAddressAll, parameters) recover { case _: EmailErrorResponse =>
      logger.error("[SendEmailServiceImpl] [sendPassengerEmail] Error in sending email")
      true
    }

}
