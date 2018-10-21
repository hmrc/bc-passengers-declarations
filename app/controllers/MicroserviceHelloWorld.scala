package controllers

import javax.inject.Inject
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.Future

class MicroserviceHelloWorld @Inject()(cc: ControllerComponents) extends BackendController(cc) {

	def hello() = Action.async { implicit request =>
		Future.successful(Ok("Hello world"))
	}

}
