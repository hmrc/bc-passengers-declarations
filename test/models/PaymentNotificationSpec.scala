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

package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*

class PaymentNotificationSpec extends AnyWordSpec with Matchers {

  "PaymentNotification" should {

    "serialize to JSON" in {
      val notification  = PaymentNotification(
        status = PaymentNotification.Successful,
        reference = ChargeReference(123)
      )
      val json: JsValue = Json.toJson(notification)
      (json \ "status").as[String]    shouldBe PaymentNotification.Successful
      (json \ "reference").as[String] shouldBe "XFPR0000000123"
    }

    "deserialize from JSON" in {
      val json: JsValue = Json.parse(
        """
          |{
          |  "status": "Successful",
          |  "reference": "XFPR0000000123"
          |}
          |""".stripMargin
      )
      val notification  = json.as[PaymentNotification]
      notification.status          shouldBe PaymentNotification.Successful
      notification.reference.value shouldBe 123
    }

    "handle different statuses" in {
      val statuses = Seq(PaymentNotification.Successful, PaymentNotification.Failed, PaymentNotification.Cancelled)
      statuses.foreach { status =>
        val notification  = PaymentNotification(
          status = status,
          reference = ChargeReference(123)
        )
        val json: JsValue = Json.toJson(notification)
        (json \ "status").as[String]    shouldBe status
        (json \ "reference").as[String] shouldBe "XFPR0000000123"
      }
    }
  }
}
