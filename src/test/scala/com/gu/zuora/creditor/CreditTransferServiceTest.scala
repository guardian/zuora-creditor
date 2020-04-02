package com.gu.zuora.creditor

import java.util.concurrent.atomic.AtomicInteger

import com.gu.zuora.creditor.CreditTransferService._
import com.gu.zuora.creditor.ModelReaders._
import com.gu.zuora.creditor.Models.{ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.creditor.Types.CreditBalanceAdjustmentIDs
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import org.scalatest.{FlatSpec, Matchers}

class CreditTransferServiceTest extends FlatSpec with Matchers {

  private val TestSubscriberId = "A-S012345"

  private val downloadGeneratedExportFileStub = (_: String) => None

  behavior of "CreditTransferService"

  it should "invoicesFromReport take a valid CSV export file" in {
    val expected = Set(
      NegativeInvoiceToTransfer("INV012345", -2.10, "A-S012345", "DO NOT USE MANUALLY: Holiday Credit - automated"),
      NegativeInvoiceToTransfer("INV012346", -2.11, "A-S012346", "Everyday")
    )
    val invoicesActual = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,-2.10
        |A-S012346,Everyday,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
    ))
    invoicesActual shouldEqual expected
  }

  it should "invoicesFromReport gracefully fail with an invalid CSV export file" in {

    // invalid types in the CSV etc have silent failure
    val invalidAmount = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,minustwopoundsten""".stripMargin
    ))
    assert(invalidAmount.isEmpty)

    val missingData = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance"""
    ))
    assert(missingData.isEmpty)

    val emptyResponse = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](""))
    assert(emptyResponse.isEmpty)
  }

  it should "round to the customer's benefit in processNegativeInvoicesExportLine" in {
    val roundToCustomerBenefit = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,Everyday,INV012345,2017-01-01,-2.1101""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    val negativeInvoice = roundToCustomerBenefit.head.right.get
    negativeInvoice.invoiceBalance shouldEqual -2.12
    negativeInvoice.transferrableBalance shouldEqual 2.12
  }

  it should "return a left in processNegativeInvoicesExportLine for bad data" in {

    val positiveAmountError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,Everyday,INV012345,2017-01-01,2.10""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    assert(positiveAmountError.head.isLeft)
    assert(positiveAmountError.head.left.get.startsWith("Ignored invoice INV012345"))

    val missingInvoiceNumberError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    assert(missingInvoiceNumberError.head.isLeft)
    assert(missingInvoiceNumberError.head.left.get.startsWith("Ignored invoice  dated 2017-01-01"))

    val missingSubscriberIdError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |,Everyday,INV012345,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    assert(missingSubscriberIdError.head.isLeft)
    assert(missingSubscriberIdError.head.left.get.startsWith("Ignored invoice INV012345 dated 2017-01-01 with balance -2.10 for subscription:  as"))
  }

  it should "createCreditBalanceAdjustments given to it" in {

    val adjustmentsToCreate = Seq(
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-A"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-B")
    )

    val numberOfCalls = new AtomicInteger
    val service = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls)),
      downloadGeneratedExportFileStub
    )
    val (error, success) = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    assert(numberOfCalls.intValue() == 1)
    assert(error.isEmpty)
    assert(success == Seq(
      s"Refunding-$TestSubscriberId-A",
      s"Refunding-$TestSubscriberId-B"
    ))
  }

  it should "process createCreditBalanceAdjustments in batches of 2" in {

    val adjustmentsToCreate = Seq(
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-A"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-B"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-C")
    )

    val numberOfCalls = new AtomicInteger
    val service = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls)),
      downloadGeneratedExportFileStub,
      batchSize = 2
    )
    val (error, success) = service.createCreditBalanceAdjustments(adjustmentsToCreate)

    numberOfCalls.intValue() shouldEqual 2 // also tests eager evaluation
    assert(error.isEmpty)
    success shouldEqual Seq(
      s"Refunding-$TestSubscriberId-A",
      s"Refunding-$TestSubscriberId-B",
      s"Refunding-$TestSubscriberId-C"
    )
  }

  it should "not attempt to create any createCreditBalanceAdjustments in Zuora when given no adjustments to create" in {
    val adjustmentsToCreate = Seq.empty[CreateCreditBalanceAdjustment]

    val numberOfCalls = new AtomicInteger
    val adjustCreditBalanceSpy = getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls))

    val service = new CreditTransferService(
      adjustCreditBalanceSpy,
      downloadGeneratedExportFileStub
    )
    val (error, success) = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    numberOfCalls.intValue() shouldEqual 0
    assert(error.isEmpty && success.isEmpty)
  }

  it should "handle response failures for some createCreditBalanceAdjustments" in {
    val adjustmentsToCreate = Seq(
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-A"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$TestSubscriberId-B")
    )

    val service = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(failICommandsAtIndexes = Set(0)),
      downloadGeneratedExportFileStub
    )
    val (error, success) = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    error.size shouldEqual 1
    success shouldEqual Seq(
      s"Refunding-$TestSubscriberId-B"
    )
  }

  it should "processExportFile" in {
    val adjustCreditBalanceSuccessStub = getAdjustCreditBalanceTestFunc()
    val testExportId = "123"
    val downloadGeneratedExportFileFunc = (exportId: String) => {
      if (exportId == testExportId) {
        Option(
          """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
            |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,-2.10
            |A-S012346,Everyday,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
        )
      } else None
    }
    val service = new CreditTransferService(
      adjustCreditBalanceSuccessStub,
      downloadGeneratedExportFileFunc
    )

    val adjustmentsReportActual = service.processExportFile(testExportId)

    adjustmentsReportActual shouldEqual AdjustmentsReport(
      creditBalanceAdjustmentsTotal = 2,
      negInvoicesWithHolidayCreditAutomated = 1
    )

    service.processExportFile("not-exists") shouldEqual AdjustmentsReport(
      creditBalanceAdjustmentsTotal = 0,
      negInvoicesWithHolidayCreditAutomated = 0
    )
  }

  private def createTestCreditBalanceAdjustmentCommand(invoiceId: String) = {
    CreateCreditBalanceAdjustment(
      Amount = 1.2,
      Comment = "unit test",
      ReasonCode = "Holiday Suspension Credit",
      SourceTransactionNumber = invoiceId,
      Type = "Increase"
    )
  }

  private def getAdjustCreditBalanceTestFunc(failICommandsAtIndexes: Set[Int] = Set.empty[Int],
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
