/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package helpers

import mocks.WSHttpMock
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar

trait BaseSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterEach with WSHttpMock
