package models

case class DeclarationsStatus(pendingPaymentCount: Int, paymentCompleteCount: Int, paymentFailedCount: Int, paymentCancelledCount: Int, failedSubmissionCount: Int)