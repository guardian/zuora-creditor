package com.gu.zuora.creditor

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
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
      val res = Map("numberOfInvoicesCredited" -> creditTransferService.processExportFile(exportId).toString).asJava
      logger.info(s"numberOfInvoicesCredited ${res.asScala.toMap}")
      res
    } else {
      logger.error(s"Lambda called with incorrect input data: $event")
      Map("nothingToDo" -> true.toString).asJava
    }
  }

}



