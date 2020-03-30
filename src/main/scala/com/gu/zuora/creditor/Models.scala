package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.NegativeInvoiceFileLine
import com.gu.zuora.creditor.Types.{RawCSVText, SerialisedJson, ZOQLQueryFragment}
import com.gu.zuora.soap.CreditBalanceAdjustment
import purecsv.unsafe
import purecsv.unsafe.CSVReader

object Models {



  trait ExportFileLine
  case class NegativeInvoiceFileLine(subscriptionName: String, invoiceNumber: String, invoiceDate: String, invoiceBalance: String) extends ExportFileLine
  case object NegativeInvoiceFileLine {
    val selectForZOQL: ZOQLQueryFragment =
      "SELECT Subscription.Name, Invoice.InvoiceNumber, Invoice.InvoiceDate, Invoice.Balance " +
      "FROM InvoiceItem " +
      "WHERE Invoice.Balance < 0 and Invoice.Status = 'Posted' and Subscription.AutoRenew = 'true'"
  }
  case class NegativeInvoiceToTransfer(invoiceNumber: String, invoiceBalance: BigDecimal, subscriberId: String) {
    val transferrableBalance: BigDecimal = if (invoiceBalance < 0) invoiceBalance * -1 else BigDecimal(0)
  }
  trait CreateCreditBalanceAdjustmentCommand {
    def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment
  }

  trait ExportCommand {
    def getJSON: SerialisedJson
  }

  case class ExportFile[S <: ExportFileLine](rawCSV: RawCSVText)(implicit reader: CSVReader[S]) {
    val reportLines: List[S] = {
      val allLines = reader.readCSVFromString(rawCSV)
      allLines.drop(1) // discard header row!
    }
  }
}

object ModelReaders {
  implicit val negativeInvoiceCSVReader: CSVReader[NegativeInvoiceFileLine] = unsafe.CSVReader[NegativeInvoiceFileLine]
}