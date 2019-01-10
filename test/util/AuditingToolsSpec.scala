package util

import models.ChargeReference
import models.declarations.{Declaration, State}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Injecting
import services.ValidationService

class AuditingToolsSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with Injecting {

  private lazy val auditingTools: AuditingTools = inject[AuditingTools]

  "buildDeclarationDataEvent" - {
    "must produce the expected output when supplied a declaration" in {

      val chargeReference = ChargeReference(1234567890)
      val correlationId = "fe28db96-d9db-4220-9e12-f2d267267c29"
      val data = Json.obj(
        "simpleDeclarationRequest" -> Json.obj(
          "foo" -> "bar"
        )
      )

      val declaration = Declaration(chargeReference, State.PendingPayment, correlationId, data)

      val declarationEvent = auditingTools.buildDeclarationSubmittedDataEvent(declaration)

      declarationEvent.tags mustBe Map("transactionName" -> "passengerdeclarationsubmitted")
      declarationEvent.detail mustBe data
      declarationEvent.auditSource mustBe "bc-passengers-declarations"
    }
  }
}
