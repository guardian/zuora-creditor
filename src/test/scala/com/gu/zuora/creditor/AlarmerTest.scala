package com.gu.zuora.creditor

import com.gu.zuora.creditor.Alarmer._
import org.scalatest.{FlatSpec, Matchers}

class AlarmerTest extends FlatSpec with Matchers {

  behavior of "notifyIfAdjustmentTriggered"

  private val publishToSNSStub = (_: String) => "message-id-12314"

  it should "send notification or not if negInvoicesWithHolidayCreditAutomated > 0 " in {
    notifyIfAdjustmentTriggered(AdjustmentsReport(3, 2), publishToSNSStub) shouldEqual "message-id-12314"
    notifyIfAdjustmentTriggered(AdjustmentsReport(0, 0), publishToSNSStub) shouldEqual "not-published"
  }

}
