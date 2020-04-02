package com.gu.zuora.creditor

import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import com.typesafe.scalalogging.LazyLogging

object Alarmer extends LazyLogging {

  private val SNS = AmazonSNSClient.builder().build()
  private val TopicArn = System.getenv("alarms_topic_arn")
  private val AdjustmentExecutedAlarmName = "zuora-creditor: number of Invoices credited > 0"
  private val runtimePublishSNS = (messageBody: String) => {
    SNS.publish(new PublishRequest()
      .withSubject(s"ALARM: $AdjustmentExecutedAlarmName")
      .withTargetArn(TopicArn)
      .withMessage(messageBody)).getMessageId
  }

  def notifyIfAdjustmentTriggered(adjustmentsReport: AdjustmentsReport, publishToSNS: String => String = runtimePublishSNS): String = {
    if (adjustmentsReport.negInvoicesWithHolidayCreditAutomated > 0) {

      import adjustmentsReport._

      val messageBody =
        s"""
           |You are receiving this email because zuora-creditor executed credit balance adjustments
           |
           |Total Number Of Credit Balance Adjustments = $creditBalanceAdjustmentsTotal
           |Negative invoices With 'Holiday Credit - automated' Credit = $negInvoicesWithHolidayCreditAutomated
           |
           |Alarm Details:
           |- Name: $AdjustmentExecutedAlarmName
           |- Description: IMPACT: this alarm si to inform us if credit balance adjustments for automated Holiday Credit are happening
           |For general advice, see https://docs.google.com/document/d/1_3El3cly9d7u_jPgTcRjLxmdG2e919zCLvmcFCLOYAk
           |
           |zuora-creditor repository: https://github.com/guardian/zuora-creditor
           |""".stripMargin

      logger.info(s"sending notification about numberOfInvoicesCredited > 0 to [$TopicArn]")
      publishToSNS(messageBody)
    } else "not-published"
  }

}
