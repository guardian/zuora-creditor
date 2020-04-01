package com.gu.zuora.creditor

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.PublishRequest
import com.gu.zuora.creditor.Types.KeyValue
import com.gu.zuora.creditor.holidaysuspension.GetNegativeHolidaySuspensionInvoices
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._

class Lambda extends RequestHandler[KeyValue, KeyValue] with LazyLogging {

  private val zuoraRestClient = ZuoraAPIClientFromParameterStore.zuoraRestClient
  private val zuoraGenerateExport = ZuoraExportGenerator.apply(zuoraRestClient) _

  val exportCommands = Map(
    "GetNegativeHolidaySuspensionInvoices" -> GetNegativeHolidaySuspensionInvoices
  )

  override def handleRequest(event: KeyValue, context: Context): KeyValue = {
    val shouldScheduleReport = event.containsKey("scheduleReport")
    val shouldCreditInvoices = event.containsKey("creditInvoicesFromExport")

    if (shouldScheduleReport) {
      val maybeExportId = for {
        exportCommand <- exportCommands.get(event.get("scheduleReport"))
        exportId <- zuoraGenerateExport(exportCommand)
      } yield {
        Map("creditInvoicesFromExport" -> exportId)
      }
      logger.info(s"maybeExportId $maybeExportId")
      (maybeExportId getOrElse Map("nothingMoreToDo" -> true.toString)).asJava
    } else if (shouldCreditInvoices) {

      val creditTransferService = new CreditTransferService(
        adjustCreditBalance = ZuoraCreditBalanceAdjustment.apply(zuoraRestClient),
        downloadGeneratedExportFile = ZuoraExportDownloadService.apply(zuoraRestClient)
      )
      val exportId = event.get("creditInvoicesFromExport")
      val adjustmentsCreated = creditTransferService.processExportFile(exportId)
      val result = Map("numberOfInvoicesCredited" -> adjustmentsCreated.toString).asJava
      val message = s"numberOfInvoicesCredited = $adjustmentsCreated"
      if (adjustmentsCreated > 0) notifyIfCreditBalanceAdjustmentTriggered(message)
      logger.info(message)
      result
    } else {
      logger.error(s"Lambda called with incorrect input data: $event")
      Map("nothingToDo" -> true.toString).asJava
    }
  }

  private def notifyIfCreditBalanceAdjustmentTriggered(message: String) = {
    val topicArn = System.getenv("alarms_topic_arn")
    logger.info(s"sending notification about numberOfInvoicesCredited > 0 to [$topicArn]")
    val sns = AmazonSNSClient.builder().build()
    val snsPubReq = new PublishRequest()
      .withSubject("ALARM: numberOfInvoicesCredited > 0")
      .withTargetArn(topicArn)
      .withMessage(message)
    sns.publish(snsPubReq)
  }

}



