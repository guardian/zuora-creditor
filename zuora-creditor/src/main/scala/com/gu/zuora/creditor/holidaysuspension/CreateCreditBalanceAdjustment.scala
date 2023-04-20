package com.gu.zuora.creditor.holidaysuspension

import com.gu.zuora.creditor.Models.NegativeInvoiceToTransfer
import play.api.libs.json.Json

case class BatchCreditBalanceAdjustment(objects: Seq[CreateCreditBalanceAdjustment], `type`: String)

case class CreateCreditBalanceAdjustment(
                                                 Amount: BigDecimal,
                                                 Comment: String,
                                                 ReasonCode: String,
                                                 SourceTransactionNumber: String,
                                                 Type: String
                                               )

object CreateCreditBalanceAdjustment {

  private implicit val CreditBalanceAdjustmentFormatter = Json.format[CreateCreditBalanceAdjustment]
  private implicit val BatchCreditBalanceAdjustmentFormatter = Json.format[BatchCreditBalanceAdjustment]

  def toCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreateCreditBalanceAdjustment = {
    CreateCreditBalanceAdjustment(
      Amount = invoice.transferrableBalance,
      Comment = s"${this.getClass.getSimpleName} is transferring negative Holiday Suspension " +
        s"invoice ${invoice.invoiceNumber} into subscriber ${invoice.subscriberId}'s account credit.",
      ReasonCode = "Holiday Suspension Credit",
      SourceTransactionNumber = invoice.invoiceNumber,
      Type = "Increase"
    )
  }

  def toBatchCreateJson(adjustments: Seq[CreateCreditBalanceAdjustment]): String = {
    val batchReq = BatchCreditBalanceAdjustment(
      objects = adjustments,
      `type` = "CreditBalanceAdjustment"
    )
    Json.toJson(batchReq).toString
  }
}