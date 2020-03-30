package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.ExportCommand
import com.gu.zuora.creditor.Types.SerialisedJson
import org.scalatest.FlatSpec

class ZuoraExportGeneratorTest extends FlatSpec {

  private val validCommand = new ExportCommand {
    override def getJSON: SerialisedJson = """{"valid":"command"}"""
  }

  private val invalidCommand = new ExportCommand {
    override def getJSON: SerialisedJson = """{"invalid":"command"}"""
  }

  private val zuoraRestClient = new TestRestClient {
    override def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson = {
      assert(path == "object/export")
      if (commandJSON == validCommand.getJSON) {
        """{"Id":"baz"}"""
      } else {
        ""
      }
    }
  }

  private val zuoraGenerateExport = ZuoraExportGenerator.apply(zuoraRestClient) _

  behavior of "ZuoraExportGeneratorTest"

  it should "extractExportId" in {
    import ZuoraExportGenerator.extractExportId
    assert(extractExportId("""{"foo":"bar"}""").isEmpty)
    assert(extractExportId("""{"Id":""}""").isEmpty)
    assert(extractExportId("""{"Id": 1}""").isEmpty)
    assert(extractExportId("""{Id: "bar"}""").isEmpty)
    assert(extractExportId("""{"Id":"bar"}""").contains("bar"))
  }

  it should "generate" in {
    assert(zuoraGenerateExport(validCommand).contains("baz"))
    assert(zuoraGenerateExport(invalidCommand).isEmpty)
  }


}
