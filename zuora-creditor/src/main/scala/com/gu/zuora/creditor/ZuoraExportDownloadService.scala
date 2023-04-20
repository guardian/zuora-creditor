package com.gu.zuora.creditor

import com.gu.zuora.creditor.Types.{ExportId, FileId, RawCSVText, SerialisedJson}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.Json.parse

object ZuoraExportDownloadService extends LazyLogging {

  def apply(zuoraRestClient: ZuoraRestClient, alarmer: Alarmer)(exportId: ExportId): Option[RawCSVText] = {
    def getZuoraExport(exportId: ExportId) = zuoraRestClient.makeRestGET(s"object/export/$exportId")

    def extractFileId(response: SerialisedJson) =
      (parse(response) \ "FileId").as[String]

    def extractReportStatus(response: SerialisedJson) = (parse(response) \ "Status").as[String]

    def downloadExportFile(fileId: FileId) = zuoraRestClient.downloadFile(s"file/$fileId")

    val exportResponse = getZuoraExport(exportId)
    logger.info(s"ZUORA Export response: $exportResponse")
    val reportStatus = extractReportStatus(exportResponse)
    if (reportStatus != "Completed") {
      val errorMessage =
      s"""attempting to download report which status is '$reportStatus',
           |"Consider increasing wait time between the steps in [ZuoraCreditorStepFunction]""".stripMargin
      val alarmMessageId = alarmer.notifyAboutReportDownloadFailure(errorMessage)
      throw new IllegalStateException(
      s"""$errorMessage
           |alarm was sent to SNS, messageId:$alarmMessageId""".stripMargin)
    }
    val fileId = extractFileId(exportResponse)
    val rawCSV = downloadExportFile(fileId)
    // TODO remove dependency from having a  Option[RawCSVText] type here
    // this can be RawCSVText only
    Some(rawCSV)
  }


}

