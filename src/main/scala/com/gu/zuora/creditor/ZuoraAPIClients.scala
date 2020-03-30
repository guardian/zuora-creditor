package com.gu.zuora.creditor

import java.lang.System.getenv
import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.util.Base64
import com.gu.zuora.creditor.Types.{RawCSVText, SerialisedJson}
import scalaj.http.HttpOptions.readTimeout
import scalaj.http.{HttpRequest, _}

import scala.reflect.internal.util.StringOps

trait ZuoraRestClient {
  def makeRestGET(path: String): SerialisedJson

  def downloadFile(path: String): RawCSVText

  def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson
}


object ZuoraAPIClientsFromEnvironment extends Logging {

  private def decryptSecret(cyphertext: String) = {
    if (getenv("SkipSecretDecryption") == "true") {
      cyphertext
    } else {
      val encryptedKey = ByteBuffer.wrap(Base64.decode(cyphertext))
      val client = AWSKMSClientBuilder.defaultClient
      val request = new DecryptRequest().withCiphertextBlob(encryptedKey)
      val plainTextKey = client.decrypt(request).getPlaintext
      new String(plainTextKey.array(), Charset.forName("UTF-8"))
    }
  }

  private val zuoraApiAccessKeyId = getenv("ZuoraApiAccessKeyId")
  private val zuoraApiSecretAccessKey = decryptSecret(getenv("ZuoraApiSecretAccessKey"))
  private val zuoraApiHost = getenv("ZuoraApiHost")
  private val zuoraApiTimeout = StringOps.oempty(getenv("ZuoraApiTimeout")).headOption.getOrElse("10000").toInt

  private def makeRequest(pathSuffix: String): HttpRequest = Http(s"https://rest.$zuoraApiHost/v1/$pathSuffix")
    .header("Content-type", "application/json")
    .header("Charset", "UTF-8")
    .header("Accept", "application/json")
    .header("apiAccessKeyId", zuoraApiAccessKeyId)
    .header("apiSecretAccessKey", zuoraApiSecretAccessKey)
    .option(readTimeout(zuoraApiTimeout))


  lazy val zuoraRestClient: ZuoraRestClient = new ZuoraRestClient {
    override def makeRestGET(pathSuffix: String): SerialisedJson = makeRequest(pathSuffix).asString.body

    override def downloadFile(pathSuffix: String): RawCSVText = makeRequest(pathSuffix).asString.body

    override def makeRestPOST(pathSuffix: String)(commandJSON: SerialisedJson): SerialisedJson =
      makeRequest(pathSuffix).postData(commandJSON).asString.body
  }

}
