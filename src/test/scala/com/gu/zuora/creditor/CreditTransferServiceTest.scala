package com.gu.zuora.creditor

import java.util.concurrent.atomic.AtomicInteger

import com.gu.zuora.creditor.CreditTransferService._
import com.gu.zuora.creditor.ModelReaders._
import com.gu.zuora.creditor.Models.{ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import org.scalatest.{FlatSpec, Matchers}

class CreditTransferServiceTest extends FlatSpec with Matchers {

  behavior of "processInvoicesFromReport"

  it should "take a valid CSV export file" in {
    val expected = Set(
      NegativeInvoiceToTransfer("INV012345", -2.10, "A-S012345", "DO NOT USE MANUALLY: Holiday Credit - automated"),
      NegativeInvoiceToTransfer("INV012346", -2.11, "A-S012346", "Everyday")
    )
    val invoicesActual = processInvoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,-2.10
        |A-S012346,Everyday,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
    ))
    invoicesActual shouldEqual expected
  }

  it should "gracefully fail with an invalid CSV export file" in {

    // invalid types in the CSV etc have silent failure
    val invalidAmount = processInvoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,minustwopoundsten""".stripMargin
    ))
    assert(invalidAmount.isEmpty)

    val missingData = processInvoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance"""
    ))
    assert(missingData.isEmpty)

    val emptyResponse = processInvoicesFromReport(ExportFile[NegativeInvoiceFileLine](""))
    assert(emptyResponse.isEmpty)
  }

  it should "round to the customer's benefit" in {
    val reportIn = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,Everyday,INV012345,2017-01-01,-2.1101""".stripMargin)

    val invoicesActual = processInvoicesFromReport(reportIn)

    invoicesActual.size shouldEqual 1
    invoicesActual.head shouldEqual NegativeInvoiceToTransfer("INV012345", -2.12, "A-S012345", "Everyday")
    invoicesActual.head.transferrableBalance shouldEqual 2.12
  }

  it should "return empty set for bad data" in {
    val positiveAmountError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,Everyday,INV012345,2017-01-01,2.10""".stripMargin)

    processInvoicesFromReport(positiveAmountError) shouldEqual Set.empty

    val missingInvoiceNumberError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,,2017-01-01,-2.10""".stripMargin
    )

    processInvoicesFromReport(missingInvoiceNumberError) shouldEqual Set.empty

    val missingSubscriberIdError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |,Everyday,INV012345,2017-01-01,-2.10""".stripMargin
    )

    processInvoicesFromReport(missingSubscriberIdError) shouldEqual Set.empty
  }

  behavior of "processExportFile"

  private val IgnoredValue = "123"

  private val downloadEmptyReport = (_: String) => None

  private val downloadReportWith3Invoices = (_: String) => {
    Option(
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,-2.10
        |A-S012346,Everyday,INV012346,2017-01-01,-2.11
        |A-S012355,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-02-01,-3.10
      """.stripMargin.trim
    )
  }

  private val downloadReportWith2Invoices = (_: String) => {
    Option(
      """subscriptionName,ratePlanName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,DO NOT USE MANUALLY: Holiday Credit - automated,INV012345,2017-01-01,-2.10
        |A-S012346,Everyday,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
    )
  }


  it should "process createCreditBalanceAdjustments in batches of 2" in {
    val numberOfCalls = new AtomicInteger

    val adjustmentsReportActual = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls)),
      downloadReportWith3Invoices,
      batchSize = 2
    ).processExportFile(IgnoredValue)

    adjustmentsReportActual shouldEqual AdjustmentsReport(
      creditBalanceAdjustmentsTotal = 3,
      negInvoicesWithHolidayCreditAutomated = 2
    )
    numberOfCalls.intValue() shouldEqual 2
  }

  it should "not attempt to create any createCreditBalanceAdjustments in Zuora when given no adjustments to create" in {
    val numberOfCalls = new AtomicInteger
    val service = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls)),
      downloadEmptyReport
    )
    val adjustmentsReportActual = service.processExportFile("not-exists")
    adjustmentsReportActual shouldEqual AdjustmentsReport(
      creditBalanceAdjustmentsTotal = 0,
      negInvoicesWithHolidayCreditAutomated = 0
    )
    numberOfCalls.intValue() shouldEqual 0
  }

  // throw IllegalStateException if CreditBalanceAdjustment call to ZUORA failed
  an[IllegalStateException] should be thrownBy {
    new CreditTransferService(
      getAdjustCreditBalanceTestFunc(failICommandsAtIndexes = Set(0)),
      downloadReportWith2Invoices
    ).processExportFile(IgnoredValue)
  }

  it should "process downloaded report into AdjustmentsReport that will contain details about credit transfer" +
    " adjustments execution in 1 batch" in {

    val numberOfCalls = new AtomicInteger
    val adjustmentsReportActual = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls)),
      downloadReportWith2Invoices
    ).processExportFile(IgnoredValue)

    adjustmentsReportActual shouldEqual AdjustmentsReport(
      creditBalanceAdjustmentsTotal = 2,
      negInvoicesWithHolidayCreditAutomated = 1
    )
    numberOfCalls.intValue() shouldEqual 1

    numberOfCalls.decrementAndGet()

    new CreditTransferService(
      getAdjustCreditBalanceTestFunc(callCounterOpt = Some(numberOfCalls)),
      downloadEmptyReport
    ).processExportFile("not-exists") shouldEqual AdjustmentsReport(
      creditBalanceAdjustmentsTotal = 0,
      negInvoicesWithHolidayCreditAutomated = 0
    )
    numberOfCalls.intValue() shouldEqual 0
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
