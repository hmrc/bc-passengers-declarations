/*
 * Copyright 2021 HM Revenue & Customs
 *
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

    val stream = environment.resourceAsStream(resource)
      .getOrElse(throw new IOException(s"resource not found: $resource"))

    Source.fromInputStream(stream).mkString
  }

  def getJson(resource: String): JsValue =
    Json.parse(getFile(resource))
}
