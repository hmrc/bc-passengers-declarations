/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package connectors

import javax.inject.{Inject, Singleton}
import models.SendEmailRequest
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

class EmailErrorResponse() extends NoStackTrace

@Singleton
class SendEmailConnectorImpl @Inject()(servicesConfig:ServicesConfig,
                                        val http: HttpClient) extends SendEmailConnector with HttpErrorFunctions {
  val emailDomain:String = servicesConfig.getConfString("email.domain","")
  val sendEmailURL: String = s"${servicesConfig.baseUrl("email.sendEmailURL")}/$emailDomain/email"
}

trait SendEmailConnector extends HttpErrorFunctions {
  val http: HttpClient
  val sendEmailURL : String

  def requestEmail(EmailRequest : SendEmailRequest)(implicit hc: HeaderCarrier): Future[Boolean] = {
    def errorMsg(status: String, ex: HttpException) = {
      Logger.error(s"PNGRS_EMAIL_FAILURE [SendEmailConnector] [sendEmail] request to send email returned a $status - email not sent - reason = ${ex.getMessage}")
      throw new EmailErrorResponse()
    }
    http.POST[SendEmailRequest, HttpResponse] (s"$sendEmailURL", EmailRequest) map { r =>
      r.status match {
        case ACCEPTED =>
          Logger.debug("[SendEmailConnector] [sendEmail] request to email service was successful")
          true
        case _ =>
          Logger.error(s"PNGRS_EMAIL_FAILURE [SendEmailConnector] [sendEmail] request to email service was unsuccessful with ${r.status}")
          false
      }
    } recover {
      case ex: HttpException => errorMsg(ex.responseCode.toString, ex)
      case ex: Throwable => errorMsg("0", new HttpException(ex.getMessage,0))
      case _ => errorMsg("0", new HttpException("An exception was thrown when calling the email service",0))
    }
  }
}
