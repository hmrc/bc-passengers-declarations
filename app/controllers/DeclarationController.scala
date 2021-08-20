/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import com.google.inject.{Inject, Singleton}
import models.declarations.{Declaration, State}
import models.{ChargeReference, PaymentNotification, PreviousDeclarationRequest}
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Result}
import repositories.{DeclarationsRepository, LockRepository}
import services.{SendEmailServiceImpl, ValidationService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeclarationController @Inject()(
  cc: ControllerComponents,
  repository: DeclarationsRepository,
  lockRepository: LockRepository,
  sendEmailService: SendEmailServiceImpl,
  validationService: ValidationService,
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

  def submitAmendment(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>

      request.headers.get(CorrelationIdKey) match {
        case Some(cid) => {
          val amendmentData = request.body.as[JsObject]
          val validationErrors = validationService.validator("request").validate(amendmentData)
          if (validationErrors.isEmpty) {
            val id = (amendmentData \ "simpleDeclarationRequest" \ "requestDetail" \ "declarationHeader" \ "chargeReference").as[String]
            val chargeReference = ChargeReference(id).getOrElse(throw new Exception(s"unable to extract charge reference:$id"))
            withLock(chargeReference) {
              repository.insertAmendment(amendmentData, cid, chargeReference).map {
                case declaration => Accepted(declaration.amendData.get).withHeaders(CorrelationIdKey -> cid)
                case _ => InternalServerError(s"Unable to update record $chargeReference")
              }
            }
          } else
            Future.successful(BadRequest(Json.obj("errors" -> validationErrors)).withHeaders(CorrelationIdKey -> cid))
        }
        case None =>
          Future.successful(BadRequest(Json.obj("errors" -> Json.arr("Missing X-Correlation-ID header"))))
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
                      updateAmendState(declaration, paymentNotification)
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

  def retrieveDeclaration(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      request.body.validate[PreviousDeclarationRequest].map {
        retrieveDeclarationRequest =>
            repository.get(retrieveDeclarationRequest).flatMap {
              _.map {
                declarationResponse =>
                  Future.successful(Ok(Json.toJsObject(declarationResponse)))
              }.getOrElse(Future.successful(NotFound))
            }
      }.recoverTotal{
        e => Future.successful(BadRequest(JsError.toJson(e)))
      }
  }

  private def updateAmendState(declaration: Declaration, paymentNotification: PaymentNotification)(implicit hc: HeaderCarrier): Future[Status] = {
    if (declaration.amendState.isDefined) {
      declaration.amendState.get match {
        case State.Paid =>
          Future.successful(Accepted)
        case State.SubmissionFailed =>
          Future.successful(Conflict)
        case _ =>
          paymentNotification.status match {
            case PaymentNotification.Successful =>
              sendEmailService.constructAndSendEmail(paymentNotification.reference)
              repository.setAmendState(paymentNotification.reference, State.Paid).map(_ => Accepted)
            case PaymentNotification.Failed =>
              repository.setAmendState(paymentNotification.reference, State.PaymentFailed).map(_ => Accepted)
            case PaymentNotification.Cancelled =>
              repository.setAmendState(paymentNotification.reference, State.PaymentCancelled).map(_ => Accepted)
          }
      }
    } else {
      Future.successful(Accepted)
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
