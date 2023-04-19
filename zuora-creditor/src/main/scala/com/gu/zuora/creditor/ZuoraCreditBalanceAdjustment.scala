package com.gu.zuora.creditor

import com.gu.zuora.creditor.Types.{CreditBalanceAdjustmentID, ErrorMessage}
import com.gu.zuora.creditor.holidaysuspension.CreateCreditBalanceAdjustment
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{JsObject, Json}

object ZuoraCreditBalanceAdjustment extends LazyLogging {
  type ZuoraCreditBalanceAdjustmentRes = Seq[Either[ErrorMessage, CreditBalanceAdjustmentID]]

  private val reqPath = "action/create"

  def apply(zuoraRestClient: ZuoraRestClient)(adjustments: Seq[CreateCreditBalanceAdjustment]): ZuoraCreditBalanceAdjustmentRes = {

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

}
