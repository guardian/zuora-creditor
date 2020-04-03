package com.gu.zuora.creditor

import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import com.gu.zuora.creditor.Alarmer.{AdjustmentExecutedAlarmName, ReportDownloadFailureAlarmName, TopicArn, logger}
import com.typesafe.scalalogging.LazyLogging

object Alarmer extends LazyLogging {

  private lazy val SNS = AmazonSNSClient.builder().build()
  private val TopicArn = System.getenv("alarms_topic_arn")

  private val Stage = System.getenv().getOrDefault("Stage", "DEV")

  val RuntimePublishSNS: (String, String) => String = (messageBody: String, alarmName: String) => {
    val msgID = SNS.publish(new PublishRequest()
      .withSubject(s"ALARM: $alarmName")
      .withTargetArn(TopicArn)
      .withMessage(messageBody)).getMessageId
    s"$alarmName Alarm message-id: $msgID"
  }
  val AdjustmentExecutedAlarmName = s"zuora-creditor $Stage: number of Invoices credited > 0"
  val ReportDownloadFailureAlarmName = s"zuora-creditor $Stage: Unable to download export of negative invoices to credit"

  def apply(publishToSNS: (String, String) => String): Alarmer = new Alarmer(publishToSNS)

  def apply: Alarmer = new Alarmer(RuntimePublishSNS)
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
           |- Name: $AdjustmentExecutedAlarmName
           |- Description: IMPACT: this alarm is to inform us if credit balance adjustments for automated Holiday Credit are happening
           |For general advice, see https://docs.google.com/document/d/1_3El3cly9d7u_jPgTcRjLxmdG2e919zCLvmcFCLOYAk
           |
           |zuora-creditor repository: https://github.com/guardian/zuora-creditor
           |""".stripMargin

      logger.info(s"sending notification about numberOfInvoicesCredited > 0 to [$TopicArn]")
      publishToSNS(messageBody, AdjustmentExecutedAlarmName)
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
         |- Name: $ReportDownloadFailureAlarmName
         |- Description: IMPACT: if this goes unaddressed ZuoraCreditorStepFunction executions are not useful
         | and no credit balance adjustments for negative invoices will take place
         |For general advice, see https://docs.google.com/document/d/1_3El3cly9d7u_jPgTcRjLxmdG2e919zCLvmcFCLOYAk
         |
         |zuora-creditor repository: https://github.com/guardian/zuora-creditor
         |""".stripMargin

    publishToSNS(messageBody, ReportDownloadFailureAlarmName)
  }
}
