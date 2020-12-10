/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package services

import connectors.{EmailErrorResponse, SendEmailConnector, SendEmailConnectorImpl}
import javax.inject.Inject
import models.declarations.Declaration
import models.{ChargeReference, SendEmailRequest}
import org.joda.time.{LocalDate, LocalTime}
import play.api.Logger
import play.api.libs.json._
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendEmailServiceImpl @Inject()(
  val emailConnector: SendEmailConnectorImpl,
  val repository: DefaultDeclarationsRepository,
  val servicesConfig: ServicesConfig,
) extends SendEmailService

trait SendEmailService {

  val passengerTemplate = "passengers_payment_confirmation"
  val emailConnector: SendEmailConnector
  val repository: DeclarationsRepository
  val servicesConfig: ServicesConfig

  private[services] def generateEmailRequest(emailAddress: Seq[String], parameters: Map[String, String]): SendEmailRequest = {
    SendEmailRequest(
      to = emailAddress,
      templateId = passengerTemplate,
      parameters,
      force = true
    )
  }

  private[services] def sendEmail(emailAddress: String, parameters: Map[String, String])(implicit hc: HeaderCarrier): Future[Boolean] = {
    val configuredEmailFirst: String = servicesConfig.getConfString("email.addressFirst", "")
    val configuredEmailSecond: String = servicesConfig.getConfString("email.addressSecond", "")
    emailConnector.requestEmail(generateEmailRequest(Seq(emailAddress, configuredEmailFirst, configuredEmailSecond), parameters)).map {
      res =>
        Logger.info("[SendEmailServiceImpl] [sendEmail] Passenger Email sent ")
        res
    }
  }

  private[services] def getEmailParamsFromData(data: JsObject): Map[String, Map[String, String]] = {
    val simpleDeclarationRequest: JsValue = data.value.apply("simpleDeclarationRequest")
    val requestCommon: JsLookupResult = simpleDeclarationRequest.\("requestCommon")
    val requestDetail: JsLookupResult = simpleDeclarationRequest.\("requestDetail")
    val personalDetails: JsLookupResult = requestDetail.\("personalDetails")
    val declarationHeader: JsLookupResult = requestDetail.\("declarationHeader")
    val liabilityDetails: JsLookupResult = requestDetail.\("liabilityDetails")
    val contactDetails: JsLookupResult = requestDetail.\("contactDetails")

    val itemsAlcohol: JsArray = requestDetail.\("declarationAlcohol") match {
      case JsDefined(value) => value.\("declarationItemAlcohol").as[JsArray]
      case _: JsUndefined => Json.arr()
    }
    val itemsTobacco: JsArray = requestDetail.\("declarationTobacco") match {
      case JsDefined(value) => value.\("declarationItemTobacco").as[JsArray]
      case _: JsUndefined => Json.arr()
    }
    val itemsOtherGoods: JsArray = requestDetail.\("declarationOther") match {
      case JsDefined(value) => value.\("declarationItemOther").as[JsArray]
      case _: JsUndefined => Json.arr()
    }
    val allItems: JsArray = itemsAlcohol ++ itemsTobacco ++ itemsOtherGoods


    val emailId: String = contactDetails.\("emailAddress").asOpt[String].getOrElse("")
    if (emailId.nonEmpty) {
      val firstName: String = personalDetails.\("firstName").asOpt[String].getOrElse("")
      val lastName: String = personalDetails.\("lastName").asOpt[String].getOrElse("")
      val fullName = s"$firstName $lastName"

      val receiptDate: String = requestCommon.\("receiptDate").asOpt[String].getOrElse("")
      val receiptDateForParsing = receiptDate.substring(0, 10)
      val receiptDateFormatted: String = if (receiptDate.equals("")) receiptDate else LocalDate.parse(receiptDateForParsing).toString("dd MMMM YYYY")

      val portOfEntry: String = declarationHeader.\("portOfEntry").asOpt[String].getOrElse("")

      val expectedDateOfArrival: String = declarationHeader.\("expectedDateOfArrival").asOpt[String].getOrElse("")
      val expectedDateArr: String = if (expectedDateOfArrival.equals("")) expectedDateOfArrival else LocalDate.parse(expectedDateOfArrival).toString("dd MMMM YYYY")

      val timeOfEntry: String = declarationHeader.\("timeOfEntry").asOpt[String].getOrElse("")
      val formattedTimeOfEntry = if (timeOfEntry.trim.equals("")) timeOfEntry else LocalTime.parse(timeOfEntry).toString("hh:mm aa").toUpperCase()

      val chargeReference: String = declarationHeader.\("chargeReference").asOpt[String].getOrElse("")

      val grandTotalGBP: String = s"£ ${liabilityDetails.\("grandTotalGBP").asOpt[String].getOrElse("")}"
      val totalExciseGBP: String = s"£ ${liabilityDetails.\("totalExciseGBP").asOpt[String].getOrElse("")}"
      val totalCustomsGBP: String = s"£ ${liabilityDetails.\("totalCustomsGBP").asOpt[String].getOrElse("")}"
      val totalVATGBP: String = s"£ ${liabilityDetails.\("totalVATGBP").asOpt[String].getOrElse("")}"

      val staticSubjectNonZero = "Receipt for payment on goods brought into the UK - Reference number "
      val staticSubjectZero = "Receipt for declaration of goods brought into Northern Ireland - Reference number "
      val dynamicSubject = if (grandTotalGBP.equalsIgnoreCase("£ 0.00")) staticSubjectZero else staticSubjectNonZero
      val subject = s"$dynamicSubject $chargeReference"

      val parameters: Map[String, String] = Map(
        "subject" -> subject,
        "NAME" -> fullName,
        "DATE" -> receiptDateFormatted,
        "PLACEOFARRIVAL" -> portOfEntry,
        "DATEOFARRIVAL" -> expectedDateArr,
        "TIMEOFARRIVAL" -> formattedTimeOfEntry,
        "REFERENCE" -> chargeReference,
        "TOTAL" -> grandTotalGBP,
        "TOTALEXCISEGBP" -> totalExciseGBP,
        "TOTALCUSTOMSGBP" -> totalCustomsGBP,
        "TOTALVATGBP" -> totalVATGBP,
        "AllITEMS" -> allItems.toString())

      return Map(emailId -> parameters)
    }
    Map.empty
  }

  def constructAndSendEmail(reference: ChargeReference)(implicit hc: HeaderCarrier): Future[Boolean] = {
    getDeclaration(reference).map(x => {
      val data: JsObject = x.data
      val emailParams = getEmailParamsFromData(data)
      val emailId = emailParams.keys.head
      val params = emailParams.getOrElse(emailId, Map.empty)
      if (emailId.nonEmpty && params.nonEmpty) {
       return sendPassengerEmail(emailId, params)
      }
      else {
        Logger.warn("[SendEmailServiceImpl] [constructAndSendEmail] Email not sent as email Id is not present in the Database")
        false
      }
    })
  }

  private[services] def getDeclaration(reference: ChargeReference): Future[Declaration] = {
    val fDec: Future[Option[Declaration]] = repository.get(reference)
    fDec.map(_.getOrElse(throw new Exception(s"Option is empty")))
  }

  private[services] def sendPassengerEmail(emailAddressAll: String, parameters: Map[String, String])(implicit hc: HeaderCarrier): Future[Boolean] =
    sendEmail(emailAddressAll, parameters) recover {
      case _: EmailErrorResponse =>
        Logger.error("[SendEmailServiceImpl] [sendPassengerEmail] Error in sending email")
        true
    }
}
