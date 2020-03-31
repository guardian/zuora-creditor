package com.gu.zuora.creditor

import java.util.concurrent.atomic.AtomicInteger

import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment

object CreditTransferServiceTestUtils {

  def createTestCreditBalanceAdjustmentCommand(invoiceId: String) = {
    CreateCreditBalanceAdjustment(
      Amount = 1.2,
      Comment = "unit test",
      ReasonCode = "Holiday Suspension Credit",
      SourceTransactionNumber = invoiceId,
      Type = "Increase"
    )
  }

  def getAdjustCreditBalanceTestFunc(failICommandsAtIndexes: Set[Int] = Set.empty[Int],
                                     callCounterOpt: Option[AtomicInteger] = None) = {
    command: Seq[CreateCreditBalanceAdjustment] => {
      callCounterOpt.foreach(_.incrementAndGet())
      command.zipWithIndex.map { case (c, idx) =>
        if (failICommandsAtIndexes.contains(idx)) Left("Error") else
          Right(c.SourceTransactionNumber)
      }
    }
  }

}
