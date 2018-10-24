package controllers

import com.google.inject.{Inject, Singleton}
import connectors.HODConnector
import models.ChargeReference
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.DeclarationsRepository
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.Future

@Singleton
class DeclarationController @Inject() (
                                        cc: ControllerComponents,
                                        repository: DeclarationsRepository,
                                        connector: HODConnector
                                      ) extends BackendController(cc) {

  def submit(): Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>

      Json.fromJson[ChargeReference](request.body) match {
        case JsSuccess(chargeReference, _) =>
          repository.insert(chargeReference, request.body).map {
            _ =>
              Accepted
          }
        case JsError(_) =>
          Future.successful(BadRequest)
      }
  }

  def update(chargeReference: ChargeReference): Action[AnyContent] = Action.async {
    implicit request =>
      repository.get(chargeReference).flatMap {
        _.map {
          json =>
            for {
              _ <- connector.submit(json)
              _ <- repository.remove(chargeReference)
            } yield Accepted
        }.getOrElse(Future.successful(NotFound))
      }
  }
}
