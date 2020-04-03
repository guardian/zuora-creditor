package com.gu.zuora.creditor

import com.gu.zuora.creditor.Types.{RawCSVText, SerialisedJson}
import org.scalatest.FlatSpec

class ZuoraExportDownloadServiceTest extends FlatSpec {

  val exportIdCompleted = "foo"
  val exportIdStillProcessing = "foo-processing"
  val unknownExportId = "bar"
  val invalidExportId = "baz"
  val fileId = "bim"
  val expectedCSV: RawCSVText =
    """lyric,lyric,lyric,lyric
      |abc,123,abc,123""".stripMargin

  private val zuoraRestClient = new ZuoraRestClient {

    override def downloadFile(path: String): RawCSVText = {
      if (path == s"file/$fileId") {
        expectedCSV
      } else {
        ""
      }
    }

    override def makeRestGET(path: String): SerialisedJson = {
      if (path == s"object/export/$exportIdCompleted") {
        s"""{"FileId":"$fileId","Status":"Completed"}"""
      } else if (path == s"object/export/$exportIdStillProcessing") {
        s"""{"FileId":"$fileId","Status":"Processing"}"""
      } else {
        """{"invalid":"invalid"}"""
      }
    }

    override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = ???
  }

  private val TestSnsMessageId = "id123"

  private val alarmerStub = Alarmer(publishToSNS = (_: String, _: String) => TestSnsMessageId)

  private val downloadGeneratedExportFile = ZuoraExportDownloadService.apply(zuoraRestClient, alarmerStub) _

  behavior of "ZuoraExportDownloaderTest"

  it should "downloadGeneratedExportFile" in {
    val csvFile = downloadGeneratedExportFile(exportIdCompleted)
    assert(csvFile.contains(expectedCSV))
  }

  it should "downloadGeneratedExportFile throws exception and sends alarm if status is not Completed" in {
    val thrown = intercept[IllegalStateException] {
      downloadGeneratedExportFile(exportIdStillProcessing)
    }

    assert(thrown.getMessage ==
      s"""attempting to download report which status is 'Processing',
         |"Consider increasing wait time between the steps in [ZuoraCreditorStepFunction]
         |alarm was sent to SNS, messageId:$TestSnsMessageId""".stripMargin)
  }

}
