package com.gu.zuora.creditor

import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import com.gu.zuora.creditor.Alarmer.{adjustmentExecutedAlarmName, reportDownloadFailureAlarmName, topicArn, logger}
import com.typesafe.scalalogging.LazyLogging

object Alarmer extends LazyLogging {

  private lazy val snsClient = AmazonSNSClient.builder().build()
  private val topicArn = System.getenv("alarms_topic_arn")

  private val stage = System.getenv().getOrDefault("Stage", "DEV")

  val runtimePublishSNS: (String, String) => String = (messageBody: String, alarmName: String) => {
    val msgID = snsClient.publish(new PublishRequest()
      .withSubject(s"ALARM: $alarmName")
      .withTargetArn(topicArn)
      .withMessage(messageBody)).getMessageId
    s"$alarmName Alarm message-id: $msgID"
  }
  val adjustmentExecutedAlarmName = s"zuora-creditor $stage: number of Invoices credited > 0"
  val reportDownloadFailureAlarmName = s"zuora-creditor $stage: Unable to download export of negative invoices to credit"

  def apply(publishToSNS: (String, String) => String): Alarmer = new Alarmer(publishToSNS)

  def apply: Alarmer = new Alarmer(runtimePublishSNS)
}

class Alarmer(publishToSNS: (String, String) => String) extends LazyLogging {
  def notifyIfAdjustmentTriggered(adjustmentsReport: AdjustmentsReport): String = {
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
           |- Name: $adjustmentExecutedAlarmName
           |- Description: IMPACT: this alarm is to inform us if credit balance adjustments for automated Holiday Credit are happening
           |For general advice, see https://docs.google.com/document/d/1_3El3cly9d7u_jPgTcRjLxmdG2e919zCLvmcFCLOYAk
           |
           |zuora-creditor repository: https://github.com/guardian/zuora-creditor
           |""".stripMargin

      logger.info(s"sending notification about numberOfInvoicesCredited > 0 to [$topicArn]")
      publishToSNS(messageBody, adjustmentExecutedAlarmName)
    } else "not-published"
  }

  def notifyAboutReportDownloadFailure(errorMessage: String): String = {
    val messageBody =
      s"""
         |You are receiving this email because zuora-creditor was Unable to download export of negative invoices to credit
         |
         |Error message:
         |$errorMessage
         |
         |Alarm Details:
         |- Name: $reportDownloadFailureAlarmName
         |- Description: IMPACT: if this goes unaddressed ZuoraCreditorStepFunction executions are not useful
         | and no credit balance adjustments for negative invoices will take place
         |For general advice, see https://docs.google.com/document/d/1_3El3cly9d7u_jPgTcRjLxmdG2e919zCLvmcFCLOYAk
         |
         |zuora-creditor repository: https://github.com/guardian/zuora-creditor
         |""".stripMargin

    publishToSNS(messageBody, reportDownloadFailureAlarmName)
  }
}
