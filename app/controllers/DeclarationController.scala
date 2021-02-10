/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package controllers

import com.google.inject.{Inject, Singleton}
import models.declarations.State
import models.{ChargeReference, PaymentNotification}
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Result}
import repositories.{DeclarationsRepository, LockRepository}
import services.SendEmailServiceImpl
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeclarationController @Inject()(
  cc: ControllerComponents,
  repository: DeclarationsRepository,
  lockRepository: LockRepository,
  sendEmailService: SendEmailServiceImpl
)(implicit ec: ExecutionContext) extends BackendController(cc) {

  val CorrelationIdKey = "X-Correlation-ID"

  def submit(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>

      request.headers.get(CorrelationIdKey) match {
        case Some(cid) =>
          repository.insert(request.body.as[JsObject], cid,false).map {
            case Right(declaration) =>
              Accepted(declaration.data).withHeaders(CorrelationIdKey -> cid)
            case Left(errors) =>
              BadRequest(Json.obj("errors" -> errors)).withHeaders(CorrelationIdKey -> cid)
          }
        case None =>
          Future.successful {
            BadRequest(Json.obj("errors" -> Json.arr("Missing X-Correlation-ID header")))
          }
      }
  }

  def update(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      request.body.validate[PaymentNotification].map {
        paymentNotification =>
          withLock(paymentNotification.reference) {
            repository.get(paymentNotification.reference).flatMap {
              _.map {
                declaration =>
                  declaration.state match {
                    case State.Paid =>
                      Future.successful(Accepted)
                    case State.SubmissionFailed =>
                      Future.successful(Conflict)
                    case _ =>
                      paymentNotification.status match {
                        case PaymentNotification.Successful =>
                          sendEmailService.constructAndSendEmail(paymentNotification.reference)
                          repository.setState(paymentNotification.reference, State.Paid).map(_ => Accepted)
                        case PaymentNotification.Failed =>
                          repository.setState(paymentNotification.reference, State.PaymentFailed).map(_ => Accepted)
                        case PaymentNotification.Cancelled =>
                          repository.setState(paymentNotification.reference, State.PaymentCancelled).map(_ => Accepted)
                      }
                  }

              }.getOrElse(Future.successful(NotFound))
            }
          }
      }.recoverTotal{
        e => Future.successful(BadRequest(JsError.toJson(e)))
      }
  }

  private def withLock(chargeReference: ChargeReference)(f: => Future[Result]): Future[Result] = {
    lockRepository.lock(chargeReference.value).flatMap {
      gotLock =>
        if (gotLock) {
          f.flatMap {
            result =>
              lockRepository.release(chargeReference.value).map {
                _ =>
                  result
              }
          }.recoverWith {
            case e =>
              lockRepository.release(chargeReference.value)
                .map { _ => throw e }
          }
        } else {
          Future.successful(Locked)
        }
    }
  }

}
