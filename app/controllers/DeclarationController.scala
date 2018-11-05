package controllers

import com.google.inject.{Inject, Singleton}
import models.ChargeReference
import models.declarations.State
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.DeclarationsRepository
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeclarationController @Inject() (
                                        cc: ControllerComponents,
                                        repository: DeclarationsRepository
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
      repository.get(chargeReference).flatMap {
        _.map {
          _ =>
            repository.setState(chargeReference, State.Paid).map(_ => Accepted)
        }.getOrElse(Future.successful(NotFound))
      }
  }
}
