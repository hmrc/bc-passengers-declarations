package services

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.{JsonSchema, JsonSchemaFactory}
import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}

import scala.collection.JavaConverters._

class Validator(schema: JsonSchema) {

  def validate(jsValue: JsValue): List[String] = {

    val json       = JsonLoader.fromString(Json.stringify(jsValue))
    val result     = schema.validate(json)

    if (result.isSuccess) {
      List.empty
    } else {
      result.iterator.asScala.toList.map {
        _.getMessage
      }
    }
  }
}

class ValidationService @Inject() (
                                    resourceService: ResourceService
                                  ) {

  private val factory = JsonSchemaFactory.byDefault()

  def get(schemaName: String): Validator = {

    val schemaJson = JsonLoader.fromString(resourceService.getFile(schemaName))
    val schema     = factory.getJsonSchema(schemaJson)

    new Validator(schema)
  }
}
