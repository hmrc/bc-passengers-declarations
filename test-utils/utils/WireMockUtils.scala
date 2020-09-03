/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.apache.bcel.verifier.exc.VerificationException

object WireMockUtils {

  implicit class WireMockServerImprovements(val server: WireMockServer) {

    def requestsWereSent(times: Int, request: RequestPatternBuilder): Boolean = {
      try {
        server.verify(times, request)
        true
      } catch {
        case _: VerificationException => false
      }
    }
  }
}