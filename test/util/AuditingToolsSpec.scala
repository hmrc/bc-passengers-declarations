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

import models.declarations.Etmp
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting
class AuditingToolsSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with Injecting with Constants {

  private lazy val auditingTools: AuditingTools = inject[AuditingTools]

  "buildDeclarationDataEvent" - {
    "must produce the expected output when supplied a declaration" in {

      val declarationEvent = auditingTools.buildDeclarationSubmittedDataEvent(declaration.data)

      declarationEvent.tags mustBe Map("transactionName" -> "passengerdeclarationsubmitted")
      declarationEvent.detail mustBe Json.toJsObject(declarationData.as[Etmp])
      declarationEvent.auditSource mustBe "bc-passengers-declarations"
    }
  }
}
