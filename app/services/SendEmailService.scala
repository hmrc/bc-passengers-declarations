/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package services

import connectors.{EmailErrorResponse, SendEmailConnector, SendEmailConnectorImpl}
import javax.inject.Inject
import models.declarations.Declaration
import models.{ChargeReference, SendEmailRequest}
import play.api.Logger
import play.api.libs.json.{JsLookupResult, JsObject, JsValue}
import repositories.{DeclarationsRepository, DefaultDeclarationsRepository}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendEmailServiceImpl @Inject()(
        val emailConnector: SendEmailConnectorImpl,
        val repository: DefaultDeclarationsRepository
      ) extends SendEmailService

trait SendEmailService {

    val passengerTemplate = "passengers_payment_confirmation"
    val emailConnector: SendEmailConnector
    val repository: DeclarationsRepository

    private[services] def generateEmailRequest(emailAddress: Seq[String], parameters: Map[String,String]): SendEmailRequest = {
      SendEmailRequest(
        to = emailAddress,
        templateId = passengerTemplate,
        parameters,
        force = true
      )
    }

    def sendEmail(emailAddress :String, parameters: Map[String,String])(implicit hc: HeaderCarrier): Future[Boolean] = {
      emailConnector.requestEmail(generateEmailRequest(Seq(emailAddress),parameters)).map {
        res => Logger.info("Passenger Email sent ")
          res
      }
    }

   def constructEmail(reference: ChargeReference)(implicit hc: HeaderCarrier):Unit  = {
    getDeclaration(reference).map( x => {
      val data: JsObject = x.data
      val simpleDeclarationRequest:JsValue = data.value.apply("simpleDeclarationRequest")
      val requestCommon: JsLookupResult = simpleDeclarationRequest.\("requestCommon")
      val requestDetail: JsLookupResult = simpleDeclarationRequest.\("requestDetail")
      val personalDetails: JsLookupResult = requestDetail.\("personalDetails")
      val declarationHeader: JsLookupResult = requestDetail.\("declarationHeader")
      val liabilityDetails: JsLookupResult = requestDetail.\("liabilityDetails")
      val contactDetails: JsLookupResult = requestDetail.\("personalDetails")

      val emailId: String = (contactDetails.\("emailAddress").asOpt[String]).getOrElse("")
      if(emailId.nonEmpty){
        val firstName:String = (personalDetails.\("firstName").asOpt[String]).getOrElse("")
        val lastName:String = (personalDetails.\("lastName").asOpt[String]).getOrElse("")
        val fullName = s"$firstName $lastName"
        val receiptDate:String = (requestCommon.\("receiptDate").asOpt[String]).getOrElse("")
        val portOfEntry:String = (declarationHeader.\("portOfEntry").asOpt[String]).getOrElse("")
        val expectedDateOfArrival:String = (declarationHeader.\("expectedDateOfArrival").asOpt[String]).getOrElse("")
        val timeOfEntry:String = (declarationHeader.\("timeOfEntry").asOpt[String]).getOrElse("")
        val chargeReference:String = (declarationHeader.\("chargeReference").asOpt[String]).getOrElse("")
        val grandTotalGBP:String = "£ " + (liabilityDetails.\("grandTotalGBP").asOpt[String]).getOrElse("")

        val parameters: Map[String,String] = Map(
          "NAME" -> fullName,
          "DATE" -> receiptDate,
          "PLACEOFARRIVAL" -> portOfEntry,
          "DATEOFARRIVAL" -> expectedDateOfArrival,
          "TIMEOFARRIVAL" -> timeOfEntry,
          "REFERENCE" -> chargeReference,
          "TRANSACTIONREFERENCE" -> chargeReference,
          "TOTAL" -> grandTotalGBP,
          "NAME_0" -> "15 litres spirits",
          "COSTGBP_0" -> "10.50",
          "NAME_1" -> "All other electronic devices",
          "COSTGBP_1" -> "11.50",
          "NAME_2" -> "Fender Guitar",
          "COSTGBP_2" -> "12.50")


        sendPassengerEmail(emailId,parameters)
      }
    })
  }

  private def getDeclaration (reference: ChargeReference): Future[Declaration] = {
    val fDec: Future[Option[Declaration]]  =repository.get(reference)
    fDec.map(_.getOrElse(throw new Exception(s"Option is empty")))
  }

  private def sendPassengerEmail(emailAddressAll :String, parameters: Map[String,String])(implicit hc: HeaderCarrier): Future[Boolean] =
    sendEmail(emailAddressAll,parameters) recover {
      case _: EmailErrorResponse => true
    }

  }
