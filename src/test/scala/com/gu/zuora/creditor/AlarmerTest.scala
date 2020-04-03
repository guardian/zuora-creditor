package com.gu.zuora.creditor

import com.gu.zuora.creditor.Alarmer._
import org.scalatest.{FlatSpec, Matchers}

class AlarmerTest extends FlatSpec with Matchers {

  behavior of "notifyIfAdjustmentTriggered"

  private val TestMessageId = "message-id-12314"
  private val publishToSNSMock = (_: String, alarmName: String) => s"$alarmName Alarm message-id: $TestMessageId"

  private val alarmer = Alarmer(publishToSNSMock)

  it should "send notification with correct alarm name or not if negInvoicesWithHolidayCreditAutomated > 0 " in {
    alarmer.notifyIfAdjustmentTriggered(AdjustmentsReport(3, 2)) shouldEqual s"$AdjustmentExecutedAlarmName Alarm message-id: $TestMessageId"
    alarmer.notifyIfAdjustmentTriggered(AdjustmentsReport(0, 0)) shouldEqual "not-published"
  }

  behavior of "notifyAboutReportDownloadFailure"

  it should  "send notification with correct alarm name for Report Download Failure" in {
    alarmer.notifyAboutReportDownloadFailure("message") shouldEqual s"$ReportDownloadFailureAlarmName Alarm message-id: $TestMessageId"
  }
}
