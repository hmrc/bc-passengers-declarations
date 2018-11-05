package controllers

import com.google.inject.{Inject, Singleton}
import models.ChargeReference
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

  def submit(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>

      repository.insert(request.body.as[JsObject]).map {
        declaration =>
          Accepted(Json.toJson(declaration))
      }
  }

  def update(chargeReference: ChargeReference): Action[AnyContent] = Action.async {
    implicit request =>

      withLock(chargeReference) {
        repository.get(chargeReference).flatMap {
          _.map {
            declaration =>
              declaration.state match {
                case State.PendingPayment =>
                  repository.setState(chargeReference, State.Paid).map(_ => Accepted)
                case State.Paid =>
                  Future.successful(Accepted)
                case _ =>
                  Future.successful(Conflict)
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
