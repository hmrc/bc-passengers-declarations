package controllers

import com.google.inject.{Inject, Singleton}
import models.ChargeReference
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.BackendController

@Singleton
class DeclarationController @Inject() (cc: ControllerComponents) extends BackendController(cc) {

  def submit: Action[JsValue] = Action(parse.tolerantJson) {
    implicit request =>

      Json.fromJson[ChargeReference](request.body) match {
        case JsSuccess(_, _) =>
          Accepted
        case JsError(_) =>
          BadRequest
      }
  }

  def update: Action[JsValue] = Action.async(parse.tolerantJson) {
    implicit request =>
      ???
  }
}
