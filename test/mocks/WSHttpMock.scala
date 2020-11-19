/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package mocks

import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future

trait WSHttpMock {
  this: MockitoSugar =>

  lazy val mockWSHttp: HttpClient = mock[HttpClient]

  def mockHttpGet[T](url: String, thenReturn: T): OngoingStubbing[Future[T]] = {
    when(mockWSHttp.GET[T](Matchers.anyString())(Matchers.any[HttpReads[T]](), Matchers.any[HeaderCarrier](), Matchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpGet[T](url: String, thenReturn: Future[T]): OngoingStubbing[Future[T]] = {
    when(mockWSHttp.GET[T](Matchers.anyString())(Matchers.any[HttpReads[T]](), Matchers.any[HeaderCarrier](), Matchers.any()))
      .thenReturn(thenReturn)
  }

  def mockHttpPOST[I, O](url: String, thenReturn: O, mockWSHttp: HttpClient = mockWSHttp): OngoingStubbing[Future[O]] = {
    when(mockWSHttp.POST[I, O](Matchers.anyString(), Matchers.any[I](), Matchers.any())
      (Matchers.any[Writes[I]](), Matchers.any[HttpReads[O]](), Matchers.any[HeaderCarrier](), Matchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }

  def mockHttpPUT[I, O](url: String, thenReturn: O, mockWSHttp: HttpClient = mockWSHttp): OngoingStubbing[Future[O]] = {
    when(mockWSHttp.PUT[I, O](Matchers.anyString(), Matchers.any[I]())
      (Matchers.any[Writes[I]](), Matchers.any[HttpReads[O]](), Matchers.any[HeaderCarrier](), Matchers.any()))
      .thenReturn(Future.successful(thenReturn))
  }
}
