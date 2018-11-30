package controllers

import com.google.inject.{Inject, Singleton}
import models.{ChargeReference, PaymentNotification}
import models.declarations.State
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import repositories.{DeclarationsRepository, LockRepository}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeclarationController @Inject()(
  cc: ControllerComponents,
  repository: DeclarationsRepository,
  lockRepository: LockRepository
)(implicit ec: ExecutionContext) extends BackendController(cc) {

  val CorrelationIdKey = "X-Correlation-ID"

  def submit(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>

      request.headers.get(CorrelationIdKey) match {
        case Some(cid) =>
          repository.insert(request.body.as[JsObject], cid).map {
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

  def update(): Action[PaymentNotification] = Action.async(parse.tolerantJson.map(_.as[PaymentNotification])) {
    implicit request =>

      withLock(request.body.reference) {
        repository.get(request.body.reference).flatMap {
          _.map {
            declaration =>

              declaration.state match {

                case State.Paid =>
                  Future.successful(Accepted)

                case State.SubmissionFailed =>
                  Future.successful(Conflict)

                case _ =>
                  request.body.status match {
                    case PaymentNotification.Successful =>
                      repository.setState(request.body.reference, State.Paid).map(_ => Accepted)
                    case PaymentNotification.Failed =>
                      repository.setState(request.body.reference, State.PaymentFailed).map(_ => Accepted)
                    case PaymentNotification.Cancelled =>
                      repository.setState(request.body.reference, State.PaymentCancelled).map(_ => Accepted)
                  }
              }

          }.getOrElse(Future.successful(NotFound))
        }
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
