/*
 * Copyright 2025 HM Revenue & Customs
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

import java.io.IOException

import com.google.inject.{Inject, Singleton}
import play.api.Environment
import play.api.libs.json.{JsValue, Json}

import scala.io.Source

@Singleton
class ResourceService @Inject() (
  environment: Environment
) {

  def getFile(resource: String): String = {

    val stream = environment
      .resourceAsStream(resource)
      .getOrElse(throw new IOException(s"resource not found: $resource"))

    Source.fromInputStream(stream).mkString
  }

  def getJson(resource: String): JsValue =
    Json.parse(getFile(resource))
}
