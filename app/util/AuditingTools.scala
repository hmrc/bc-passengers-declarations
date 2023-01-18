/*
 * Copyright 2023 HM Revenue & Customs
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

package util

import javax.inject.{Inject, Named, Singleton}
import models.declarations.Etmp
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

@Singleton
class AuditingTools @Inject() (
  @Named("appName") val appName: String
) {

  def buildDeclarationSubmittedDataEvent(data: JsObject) =
    ExtendedDataEvent(
      auditSource = appName,
      auditType = "passengerdeclaration",
      tags = Map("transactionName" -> "passengerdeclarationsubmitted"),
      detail = Json.toJsObject(data.as[Etmp])
    )
}
