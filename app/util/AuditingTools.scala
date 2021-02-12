/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package util

import javax.inject.{Inject, Named, Singleton}
import models.declarations.Declaration
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

@Singleton
class AuditingTools @Inject() (
  @Named("appName") val appName: String
) {

  def buildDeclarationSubmittedDataEvent(declaration: Declaration) = {
    ExtendedDataEvent(
      auditSource = appName,
      auditType =  "passengerdeclaration",
      tags = Map("transactionName" -> "passengerdeclarationsubmitted"),
      detail = declaration.data
    )
  }
}
