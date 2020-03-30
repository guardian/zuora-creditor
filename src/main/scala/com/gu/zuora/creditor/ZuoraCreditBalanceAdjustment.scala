package com.gu.zuora.creditor

import com.gu.zuora.creditor.Types.{CreditBalanceAdjustmentID, ErrorMessage}
import com.gu.zuora.creditor.ZuoraCreditBalanceAdjustment.ZuoraCreditBalanceAdjustmentRes
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.util.Try

object ZuoraCreditBalanceAdjustment {
  type ZuoraCreditBalanceAdjustmentRes = Seq[Either[ErrorMessage, CreditBalanceAdjustmentID]]
}

// TODO refactor how we pass zuoraRestClient

class ZuoraCreditBalanceAdjustment(implicit zuoraRestClient: ZuoraRestClient) extends Logging {

  private val reqPath = "action/create"

  def apply(adjustments: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {

    val reqBody = CreateCreditBalanceAdjustment.toBatchCreateJson(adjustments)

    logger.info(s"performing ZuoraCreditBalanceAdjustment at: path: '$reqPath' , body: $reqBody")

    val rawResponse = zuoraRestClient.makeRestPOST(reqPath)(reqBody)
    val response = Json.parse(rawResponse).as[Seq[JsObject]]
    response.map { json =>
      val isSuccess = (json \ "Success").as[Boolean]
      if (isSuccess) Right((json \ "Id").as[String])
      else Left((json \ "Errors").get.toString)
    }
  }

  def extractExportId(json: JsValue): Option[String] = {
    Try((json \ "Id").asOpt[String]).toOption.flatten.filter(_.nonEmpty)
  }

}
