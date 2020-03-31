package com.gu.zuora.creditor

import java.util.concurrent.atomic.AtomicInteger

import com.gu.zuora.creditor.CreditTransferService._
import com.gu.zuora.creditor.ModelReaders._
import com.gu.zuora.creditor.Models.{ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.creditor.Types.{CreditBalanceAdjustmentIDs, RawCSVText, SerialisedJson}
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import org.scalatest.{FlatSpec, Matchers}
import CreditTransferServiceTestUtils._

class CreditTransferServiceTest extends FlatSpec with Matchers {

  private val TestSubscriberId = "A-S012345"

  private val ZuoraRestClientStub = new ZuoraRestClient {
    override def makeRestGET(path: String): SerialisedJson = ???

    override def downloadFile(path: String): RawCSVText = ???

    override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = ???
  }

  private val downloadGeneratedExportFileFunc = ZuoraExportDownloadService.apply(ZuoraRestClientStub) _

  behavior of "CreditTransferService"

  it should "invoicesFromReport take a valid CSV export file" in {
    val expected = Set(
      NegativeInvoiceToTransfer("INV012345", -2.10, "A-S012345"),
      NegativeInvoiceToTransfer("INV012346", -2.11, "A-S012346")
    )
    val invoicesActual = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,-2.10
        |A-S012346,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
    ))
    invoicesActual shouldEqual expected
  }

  it should "invoicesFromReport gracefully fail with an invalid CSV export file" in {

    // invalid types in the CSV etc have silent failure
    val invalidAmount = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,minustwopoundsten""".stripMargin
    ))
    assert(invalidAmount.isEmpty)

    val missingData = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance"""
    ))
    assert(missingData.isEmpty)

    val emptyResponse = invoicesFromReport(ExportFile[NegativeInvoiceFileLine](""))
    assert(emptyResponse.isEmpty)
  }

  it should "round to the customer's benefit in processNegativeInvoicesExportLine" in {
    val roundToCustomerBenefit = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,-2.1101""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    val negativeInvoice = roundToCustomerBenefit.head.right.get
    negativeInvoice.invoiceBalance shouldEqual -2.12
    negativeInvoice.transferrableBalance shouldEqual 2.12
  }

  it should "return a left in processNegativeInvoicesExportLine for bad data" in {

    val positiveAmountError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,2.10""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    assert(positiveAmountError.head.isLeft)
    assert(positiveAmountError.head.left.get.startsWith("Ignored invoice INV012345"))

    val missingInvoiceNumberError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(processNegativeInvoicesExportLine)
    assert(missingInvoiceNumberError.head.isLeft)
    assert(missingInvoiceNumberError.head.left.get.startsWith("Ignored invoice  dated 2017-01-01"))

    val missingSubscriberIdError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |,INV012345,2017-01-01,-2.10""".stripMargin
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
      downloadGeneratedExportFileFunc
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
      downloadGeneratedExportFileFunc,
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
      downloadGeneratedExportFileFunc
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
      downloadGeneratedExportFileFunc
    )
    val (error, success) = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    error.size shouldEqual 1
    success shouldEqual Seq(
      s"Refunding-$TestSubscriberId-B"
    )
  }

  it should "makeCreditAdjustments for no invoices" in {
    val adjustCreditBalanceSuccessStub = getAdjustCreditBalanceTestFunc()
    val service = new CreditTransferService(
      adjustCreditBalanceSuccessStub,
      downloadGeneratedExportFileFunc
    )
    service.makeCreditAdjustments(Set.empty) shouldEqual Seq.empty
  }

  it should "makeCreditAdjustments" in {
    val invoices = Set(
      NegativeInvoiceToTransfer("INV012345", BigDecimal(-2.1).setScale(2), TestSubscriberId),
      NegativeInvoiceToTransfer("INV012346", -2.111111, TestSubscriberId)
    )

    val service = new CreditTransferService(
      getAdjustCreditBalanceTestFunc(),
      downloadGeneratedExportFileFunc
    )
    val expected: CreditBalanceAdjustmentIDs = Seq("INV012345", "INV012346")

    val createdAdjustments = service.makeCreditAdjustments(invoices)
    createdAdjustments shouldEqual expected
  }

}
