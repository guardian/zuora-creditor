package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.{ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.creditor.Types._
import com.gu.zuora.creditor.ZuoraCreditBalanceAdjustment.ZuoraCreditBalanceAdjustmentRes
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment._
import com.typesafe.scalalogging.LazyLogging

import scala.math.BigDecimal.RoundingMode.UP
import scala.util.{Left, Right, Try}

object CreditTransferService extends LazyLogging {

  // https://www.zuora.com/developer/api-reference/#tag/Actions
  // operations allow changes to up-to 50 objects at a time
  val maxNumberOfCreateObjects = 50

  def processInvoicesFromReport(report: NegativeInvoiceReport): Set[NegativeInvoiceToTransfer] = {
    report.reportLines.flatMap { reportLine =>
      val result = processNegativeInvoicesExportLine(reportLine)
      result.left.foreach(x => logger.warn(x))
      result.right.toOption
    }.toSet
  }

  private def processNegativeInvoicesExportLine(reportLine: NegativeInvoiceFileLine): Either[ErrorMessage, NegativeInvoiceToTransfer] = {
    // Not HALF_EVEN rounding - we always round to the customer's benefit using UP
    val invoiceBalance = Try(BigDecimal.apply(reportLine.invoiceBalance).setScale(2, UP)).toOption
    val invoiceNumberIsValid = reportLine.invoiceNumber.nonEmpty
    val subscriptionNameIsValid = reportLine.subscriptionName.nonEmpty

    if (invoiceBalance.exists(_ < 0) && invoiceNumberIsValid && subscriptionNameIsValid) {
      Right(NegativeInvoiceToTransfer(reportLine.invoiceNumber, invoiceBalance.get, reportLine.subscriptionName, reportLine.ratePlanName))
    } else {
      Left(s"Ignored invoice ${reportLine.invoiceNumber} dated ${reportLine.invoiceDate} with balance ${reportLine.invoiceBalance} for subscription: " +
        s"${reportLine.subscriptionName} as its balance is not negative or some other piece of information is missing")
    }
  }
}

class CreditTransferService(
                             adjustCreditBalance: Seq[CreateCreditBalanceAdjustment] => ZuoraCreditBalanceAdjustmentRes,
                             downloadGeneratedExportFile: ExportId => Option[RawCSVText],
                             batchSize: Int = CreditTransferService.maxNumberOfCreateObjects) extends LazyLogging {

  import CreditTransferService._
  import ModelReaders._

  def processExportFile(exportId: ExportId): AdjustmentsReport = {
    val maybeExportCSV = downloadGeneratedExportFile(exportId)
    maybeExportCSV.foreach { rawCSV =>
      logger.info("==========================================")
      logger.info(s"csv export: $rawCSV")
      logger.info("==========================================")
    }
    val invoicesWhichNeedCrediting = maybeExportCSV.map(x => processInvoicesFromReport(ExportFile(x))).getOrElse {
      logger.error("Unable to download export of negative invoices to credit")
      Set.empty[NegativeInvoiceToTransfer]
    }
    if (invoicesWhichNeedCrediting.isEmpty) {
      logger.warn("No negative invoices require crediting today")
    }
    val negativeInvoicesWithAutHolidayCredit = invoicesWhichNeedCrediting.count(_.ratePlanName.toLowerCase.contains("automated"))
    val makeCreditAdjustmentsResult = makeCreditAdjustments(invoicesWhichNeedCrediting)
    AdjustmentsReport(
      creditBalanceAdjustmentsTotal = makeCreditAdjustmentsResult.size,
      negativeInvoicesWithAutHolidayCredit
    )
  }

  private def makeCreditAdjustments(invoices: Set[NegativeInvoiceToTransfer]) = {
    if (invoices.nonEmpty) {
      // Sort by invoice name to help with logging, we're going to credit them in order of their invoice number
      val invoicesToCredit = invoices.toSeq.sortBy(_.invoiceNumber)
      val allInvoiceNumbers = invoicesToCredit.map(a => s"${a.invoiceNumber} (${a.invoiceBalance})").mkString(", ")
      logger.info(s"Creating ${invoicesToCredit.length} Credit Balance Adjustments for Invoices: $allInvoiceNumbers")

      val adjustmentsToMake = invoicesToCredit.map(toCreditBalanceAdjustment)
      logger.info(s"Attempting to create ${invoices.size} Credit Balance Adjustments for Invoices: " +
        s"${invoices.map(_.invoiceNumber).mkString(", ")}")

      val (errors, success) = createCreditBalanceAdjustments(adjustmentsToMake)
      if (errors.nonEmpty) {
        val errorMsg = s"${errors.size} Errors creating Credit Balance Adjustment: ${errors.mkString(", ")}"
        logger.error(s"${errors.size} Errors creating Credit Balance Adjustment: ${errors.mkString(", ")}")
        // propagate error to AWS lambda metrics
        throw new IllegalStateException(errorMsg)
      } else {
        logger.info(s"Successfully created ${success.size} Credit Balance Adjustments with IDs: ${success.mkString(", ")}")
        success
      }
    } else Seq.empty
  }

  private def createCreditBalanceAdjustments(adjustmentsToMake: Seq[CreateCreditBalanceAdjustment]): (List[ErrorMessage], CreditBalanceAdjustmentIDs) = {
    val batches = adjustmentsToMake.grouped(batchSize).toList
    logger.info(s"createCreditBalanceAdjustments batches: $batches")
    val result = batches.flatMap(adjustCreditBalance)
    val errors = result.collect { case Left(error) => error }
    val success = result.collect { case Right(success) => success }
    (errors, success)
  }
}
