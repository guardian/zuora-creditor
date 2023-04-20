package com.gu.zuora.creditor

import java.util

import com.gu.zuora.creditor.Models.{ExportFile, NegativeInvoiceFileLine}

object Types {
  type ErrorMessage = String
  type ExportId = String
  type FileId = String
  type KeyValue = util.Map[String, String]
  type RawCSVText = String
  type NegativeInvoiceReport = ExportFile[NegativeInvoiceFileLine]
  type SerialisedJson = String
  type ZOQLQueryFragment = String
  type ZuoraSoapClientError = String
  type CreditBalanceAdjustmentID = String
  type CreditBalanceAdjustmentIDs = Seq[CreditBalanceAdjustmentID]
}
