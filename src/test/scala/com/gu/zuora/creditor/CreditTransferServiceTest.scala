package com.gu.zuora.creditor

import com.gu.zuora.creditor.ModelReaders._
import com.gu.zuora.creditor.Models.{CreateCreditBalanceAdjustmentCommand, ExportFile, NegativeInvoiceFileLine, NegativeInvoiceToTransfer}
import com.gu.zuora.creditor.Types.CreditBalanceAdjustmentIDs
import com.gu.zuora.creditor.ZuoraCreditBalanceAdjustment.ZuoraCreditBalanceAdjustmentRes
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import com.gu.zuora.soap.CreditBalanceAdjustment
import org.scalatest.FlatSpec

class CreditTransferServiceTest extends FlatSpec {

  val testSubscriberId = "A-S012345"

//  val mockCommand = new CreateCreditBalanceAdjustmentCommand {
//    override def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment = ???
//  }

  implicit val zuoraClients = new TestZuoraAPIClients

  val service = new CreditTransferService((command: Seq[CreateCreditBalanceAdjustment]) => Right(command.head.SourceTransactionNumber))

  behavior of "ZuoraCreditTransferServiceTest"


  it should "processExportFile" in {

  }

  it should "invoicesFromReport take a valid CSV export file" in {

    val expected = Set(
      NegativeInvoiceToTransfer("INV012345", -2.10, "A-S012345"),
      NegativeInvoiceToTransfer("INV012346", -2.11, "A-S012346")
    )
    val invoices = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,-2.10
        |A-S012346,INV012346,2017-01-01,-2.11
      """.stripMargin.trim
    ))
    assert(invoices.size == 2)
    assert(invoices == expected)
  }

  it should "invoicesFromReport gracefully fail with an invalid CSV export file" in {

    // invalid types in the CSV etc have silent failure
    val invalidAmount = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
      |A-S012345,INV012345,2017-01-01,minustwopoundsten""".stripMargin
    ))
    assert(invalidAmount.isEmpty)

    val missingData = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance"""
    ))
    assert(missingData.isEmpty)

    val emptyResponse = service.invoicesFromReport(ExportFile[NegativeInvoiceFileLine](""))
    assert(emptyResponse.isEmpty)
  }

  it should "round to the customer's benefit in processNegativeInvoicesExportLine" in {
    val roundToCustomerBenefit = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,-2.1101""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    val negativeInvoice = roundToCustomerBenefit.head.right.get
    assert(negativeInvoice.invoiceBalance == -2.12)
    assert(negativeInvoice.transferrableBalance == 2.12)
  }

  it should "return a left in processNegativeInvoicesExportLine for bad data" in {

    val positiveAmountError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,INV012345,2017-01-01,2.10""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    assert(positiveAmountError.head.isLeft)
    assert(positiveAmountError.head.left.get.startsWith("Ignored invoice INV012345"))

    val missingInvoiceNumberError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |A-S012345,,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    assert(missingInvoiceNumberError.head.isLeft)
    assert(missingInvoiceNumberError.head.left.get.startsWith("Ignored invoice  dated 2017-01-01"))

    val missingSubscriberIdError = ExportFile[NegativeInvoiceFileLine](
      """subscriptionName,invoiceNumber,invoiceDate,invoiceBalance
        |,INV012345,2017-01-01,-2.10""".stripMargin
    ).reportLines.map(service.processNegativeInvoicesExportLine)
    assert(missingSubscriberIdError.head.isLeft)
    assert(missingSubscriberIdError.head.left.get.startsWith("Ignored invoice INV012345 dated 2017-01-01 with balance -2.10 for subscription:  as"))
  }

  def createTestCreditBalanceAdjustmentCommand(id: String) = {
    CreateCreditBalanceAdjustment(
      Amount = 1.2,
      Comment = "unit test",
      ReasonCode = "Holiday Suspension Credit",
      SourceTransactionNumber = id,
      Type = "Increase"
    )
  }

  it should "createCreditBalanceAdjustments given to it" in {

    val adjustmentsToCreate = Seq(
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-A"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-B")
    )

    def adjustCreditBalanceSuccess(command: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {
      Right(command.head.SourceTransactionNumber)
    }

//    val numberOfCalls = new AtomicInteger
//    implicit val zuoraClients = new TestZuoraAPIClients {
//      override val zuoraSoapClient: ZuoraRestClient = getSuccessfulCreateResponse(adjustmentsToCreate, adjustmentsToCreate.length, numberOfCalls)
//    }
    val service = new CreditTransferService(adjustCreditBalanceSuccess)
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
//    assert(numberOfCalls.intValue() == 1)
    assert(created.length == 2)
    assert(created == Seq(
      s"Refunding-$testSubscriberId-A",
      s"Refunding-$testSubscriberId-B"
    ))
  }

  it should "process createCreditBalanceAdjustments in batches of 2" in {
//    val adjustmentsToCreate = Seq(
    //      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-A"))),
    //      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-B"))),
    //      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-C")))
    //    )

    val adjustmentsToCreate = Seq(
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-A"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-B"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-C")
    )

//    val numberOfCalls = new AtomicInteger
//    implicit val zuoraClients = new TestZuoraAPIClients {
//      override val zuoraSoapClient: ZuoraSoapClient = getSuccessfulCreateResponse(adjustmentsToCreate, 2, numberOfCalls)
//    }

    def adjustCreditBalanceSuccess(command: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {
      Right(command.head.SourceTransactionNumber)
    }

    val service = new CreditTransferService(adjustCreditBalanceSuccess)
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
//    assert(numberOfCalls.intValue() == 2) // also tests eager evaluation
//    assert(created.length == 3)
    assert(created == Seq(
      s"Refunding-$testSubscriberId-A",
      s"Refunding-$testSubscriberId-B",
      s"Refunding-$testSubscriberId-C"
    ))
  }

  it should "not attempt to create any createCreditBalanceAdjustments in Zuora when given no adjustments to create" in {
    val adjustmentsToCreate = Seq.empty[CreateCreditBalanceAdjustment]


//    implicit val zuoraClients = new TestZuoraAPIClients {
//      override val zuoraSoapClient: ZuoraSoapClient = new ZuoraSoapClient {
//        override def create(zObjects: Seq[ZObjectable]): Left[ZuoraSoapClientError, Nothing] = {
//          assert(false, "Should never call me")
//          Left("Should not have been called")
//        }
//      }
//    }

    def adjustCreditBalanceFailure(command: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {
      Left(command.head.SourceTransactionNumber)
    }


    val service = new CreditTransferService(adjustCreditBalanceFailure)
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    assert(created.isEmpty)
  }

  it should "handle response failures for some createCreditBalanceAdjustments" in {
//    val adjustmentsToCreate = Seq(
//      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-A"))),
//      CreditBalanceAdjustment(Id = Some(Some(s"Refunding-$testSubscriberId-B")))
//    )

    val adjustmentsToCreate = Seq(
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-A"),
      createTestCreditBalanceAdjustmentCommand(s"Refunding-$testSubscriberId-B")
    )

    def adjustCreditBalanceUnsuccessfulForHead(command: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {
      if (command.head.SourceTransactionNumber == adjustmentsToCreate.head.SourceTransactionNumber) {
        Right(command.head.SourceTransactionNumber)
      } else Left(command.head.SourceTransactionNumber)

    }

//    implicit val zuoraClients = new TestZuoraAPIClients {
//      override val zuoraSoapClient: ZuoraSoapClient = getUnsuccessfulCreateResponseForHeadSource(adjustmentsToCreate)
//    }
    val service = new CreditTransferService(adjustCreditBalanceUnsuccessfulForHead)
    val created = service.createCreditBalanceAdjustments(adjustmentsToCreate)
    assert(created == Seq(
      s"Refunding-$testSubscriberId-B"
    ))
  }

  it should "makeCreditAdjustments" in {
    assert(service.makeCreditAdjustments(Set.empty) == Seq.empty)

    // now with data

    // Also tests the scale does not get altered
    val source = Set(
      NegativeInvoiceToTransfer("INV012345", BigDecimal(-2.1).setScale(2), testSubscriberId),
      NegativeInvoiceToTransfer("INV012346", -2.111111, testSubscriberId)
    )
    val expected: CreditBalanceAdjustmentIDs = Seq(
      s"Refunding-$testSubscriberId-INV012345-2.10",
      s"Refunding-$testSubscriberId-INV012346-2.111111"
    )

    val command2 = new CreateCreditBalanceAdjustmentCommand {
      override def createCreditBalanceAdjustment(invoice: NegativeInvoiceToTransfer): CreditBalanceAdjustment = {
        CreditBalanceAdjustment(Id = Some(Some(s"Refunding-${invoice.subscriberId}-${invoice.invoiceNumber}-${invoice.transferrableBalance}")))
      }
    }

//    implicit val zuoraClients = new TestZuoraAPIClients {
//      override val zuoraSoapClient = new TestSoapClient {
//        override def create(zObjects: Seq[ZObjectable]): Either[ZuoraSoapClientError, CreateResponse] = {
//          assert(zObjects.size == source.size)
//          Right(CreateResponse(
//            result = zObjects.map(adjustment => SaveResult(Id = adjustment.Id))
//          ))
//        }
//      }
//    }

    def adjustCreditBalanceSuccess(command: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {
      Right(command.head.SourceTransactionNumber)
    }

    val service2 = new CreditTransferService(adjustCreditBalanceSuccess)

    val createdAdjustments = service2.makeCreditAdjustments(source)
    assert(createdAdjustments == expected)
  }

}
