package util

import javax.inject.{Inject, Singleton}
import models.declarations.Declaration
import play.api.Logger
import play.api.libs.json._
import services.ValidationService

@Singleton
class DeclarationDataTransformers @Inject() (val validationService: ValidationService) {

  def declarationToV110(declaration: Declaration): Option[Declaration] = {

    val updatedDeclarationHeader = (__ \ 'simpleDeclarationRequest \ 'requestDetail \ 'declarationHeader \ 'timeOfEntry).json.prune
    val renamedGoodsValueGbp = Json.stringify(declaration.data).replace("\"goodsValueGBP\"", "\"customsValueGBP\"")


    val validation = validationService.get("schema-v1-1-5.json").validate(declaration.data)
    validation match {
      case Nil =>
        Json.parse(renamedGoodsValueGbp).transform(updatedDeclarationHeader) match {
          case JsSuccess(value, _) => Some(declaration.copy(data = value))
          case _ =>
            Logger.error("Unable to transform declaration header")
            None
        }
      case errors =>
        Logger.error("Declaration data did not match 1.1.5 schema, errors:" + errors)
        None
    }
  }
}
