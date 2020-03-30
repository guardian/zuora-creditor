package com.gu.zuora.creditor

import com.gu.zuora.creditor.Types.{ExportId, FileId, RawCSVText, SerialisedJson}
import play.api.libs.json.Json.parse

import scala.util.Try

object ZuoraExportDownloadService extends Logging {

  def apply(zuoraRestClient: ZuoraRestClient)(exportId: ExportId): Option[RawCSVText] = {
    def getZuoraExport(exportId: ExportId) = zuoraRestClient.makeRestGET(s"object/export/$exportId")

    def extractFileId(response: SerialisedJson) =
      Try((parse(response) \ "FileId").asOpt[String]).toOption.flatten.filter(_.nonEmpty)

    def downloadExportFile(fileId: FileId) = zuoraRestClient.downloadFile(s"file/$fileId")

    Option(exportId).filter(_.nonEmpty).flatMap { validExportId =>
      val exportResponse = getZuoraExport(validExportId)
      val maybeFileId = extractFileId(exportResponse)
      if (maybeFileId.isEmpty) {
        logger.error(s"No FileId found in Zuora Export: $validExportId. Zuora response: $exportResponse")
      }
      maybeFileId.map(downloadExportFile).filter(_.nonEmpty)
    }
  }


}

