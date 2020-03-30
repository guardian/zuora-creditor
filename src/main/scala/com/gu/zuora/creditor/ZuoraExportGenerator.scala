package com.gu.zuora.creditor

import com.gu.zuora.creditor.Models.ExportCommand
import com.gu.zuora.creditor.Types.{ExportId, SerialisedJson}
import play.api.libs.json.Json.parse

import scala.util.Try


object ZuoraExportGenerator extends Logging {

  def apply(zuoraRestClient: ZuoraRestClient)(command: ExportCommand): Option[ExportId] = {
    def scheduleExportInZuora: Option[ExportId] = {
      val commandJSON = command.getJSON
      val response = zuoraRestClient.makeRestPOST("object/export")(commandJSON)
      if (response.isEmpty) {
        logger.error(s"Request to create Zuora Export using command: $commandJSON yielded no response")
      }
      extractExportId(response)
    }

    val maybeExportId = scheduleExportInZuora
    if (maybeExportId.isEmpty) {
      logger.error("No Zuora Export created")
    } else {
      logger.info(s"Successfully created Zuora Export: ${maybeExportId.mkString}")
    }
    maybeExportId
  }

  def extractExportId(response: SerialisedJson): Option[ExportId] =
    Try((parse(response) \ "Id").asOpt[String]).toOption.flatten.filter(_.nonEmpty)

}