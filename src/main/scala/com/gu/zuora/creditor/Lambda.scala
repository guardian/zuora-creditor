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
  private val alarmer = Alarmer.apply
  private val zuoraExportDownloadService = ZuoraExportDownloadService.apply(zuoraRestClient, alarmer) _

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
        downloadGeneratedExportFile = zuoraExportDownloadService
      )
      val exportId = event.get("creditInvoicesFromExport")
      val adjustmentsReport = creditTransferService.processExportFile(exportId)
      val adjustmentsCreated = adjustmentsReport.creditBalanceAdjustmentsTotal
      val result = Map("numberOfInvoicesCredited" -> adjustmentsCreated.toString).asJava
      alarmer.notifyIfAdjustmentTriggered(adjustmentsReport)
      logger.info(s"numberOfInvoicesCredited = $adjustmentsCreated")
      result
    } else {
      logger.error(s"Lambda called with incorrect input data: $event")
      Map("nothingToDo" -> true.toString).asJava
    }
  }
}



