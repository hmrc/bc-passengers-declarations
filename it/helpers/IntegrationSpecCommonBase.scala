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

package helpers

import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSRequest}

trait IntegrationSpecCommonBase extends AnyWordSpec with Matchers with GuiceOneServerPerSuite with
  BeforeAndAfterAll with BeforeAndAfterEach with TestSuite {

  lazy val injector = app.injector

  val sampleVRN: String = "123456789"
  val sampleVRN2: String = "123456788"
  val sampleVRN3: String = "123456787"
  val sampleEnrolmentKey: String = s"HMRC-MTD-VAT~VRN~$sampleVRN"
  val sampleEnrolmentKey2: String = s"HMRC-MTD-VAT~VRN~$sampleVRN2"
  val sampleEnrolmentKey3: String = s"HMRC-MTD-VAT~VRN~$sampleVRN3"
  val enrolmentKeyNotInMongo: String = "HMRC-MTD-VAT~VRN~123456790"
  val enrolmentKeyNotInMongo2: String = "HMRC-MTD-VAT~VRN~123456791"
  val enrolmentKeyNotInMongo3: String = "HMRC-MTD-VAT~VRN~123456792"

  val sampleJsonPayload: JsValue = Json.parse(
    """
      |{
      |   "royale": "with cheese"
      |}
      |""".stripMargin)

  val sampleJsonPayload2: JsValue = Json.parse(
    """
      |{
      |   "royale": "with cheese!!"
      |}
      |""".stripMargin)

  override def afterEach(): Unit = {
    super.afterEach()
    SharedMetricRegistries.clear()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    SharedMetricRegistries.clear()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    SharedMetricRegistries.clear()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    SharedMetricRegistries.clear()
  }

  override lazy val app = new GuiceApplicationBuilder()
    .build()

  lazy val ws = app.injector.instanceOf[WSClient]

  def buildClientForRequestToApp(baseUrl: String = "/penalties-stub", uri: String): WSRequest = {
    ws.url(s"http://localhost:$port$baseUrl$uri").withFollowRedirects(false)
  }

}
