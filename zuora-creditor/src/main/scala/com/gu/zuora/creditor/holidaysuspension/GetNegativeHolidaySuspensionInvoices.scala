package com.gu.zuora.creditor.holidaysuspension
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

import com.gu.zuora.creditor.Models.ExportCommand
import com.gu.zuora.creditor.Models.NegativeInvoiceFileLine.selectForZOQL
import com.gu.zuora.creditor.Types.SerialisedJson
import play.api.libs.json.Json

case object GetNegativeHolidaySuspensionInvoices extends ExportCommand {

  override val getJSON: SerialisedJson = {
    Json.obj(
      "Format" -> "csv",
      "Name" -> s"Negative Holiday Suspension Invoices Export - ${now.format(ISO_DATE_TIME)}",
      "Query" -> s"$selectForZOQL AND InvoiceItem.ChargeName = 'Holiday Credit'",
      "Zip" -> false
    ).toString
  }

}