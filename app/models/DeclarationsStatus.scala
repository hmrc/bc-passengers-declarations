/*
 * Copyright 2021 HM Revenue & Customs
 *
 */

package models

case class DeclarationsStatus(pendingPaymentCount: Int, paymentCompleteCount: Int, paymentFailedCount: Int, paymentCancelledCount: Int, failedSubmissionCount: Int)