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

package services

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.{JsonSchema, JsonSchemaFactory}
import com.google.inject.Inject
import play.api.Configuration
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

class ValidationService @Inject() (resourceService: ResourceService,
                                   config: Configuration) {

  private val factory = JsonSchemaFactory.byDefault()

  def get(schemaName: String): Validator = {

    val schemaJson = JsonLoader.fromString(resourceService.getFile(s"schemas/$schemaName"))
    val schema     = factory.getJsonSchema(schemaJson)

    new Validator(schema)
  }

  def validator(schemaVersion: String): Validator = {
    val schema = config.get[String](s"declarations.schemas.$schemaVersion")
    get(schema)
  }
}
