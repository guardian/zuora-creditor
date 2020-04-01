package com.gu.zuora.creditor

import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.zuora.creditor.Types.{RawCSVText, SerialisedJson}
import com.gu.{AppIdentity, AwsIdentity}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import scalaj.http.HttpOptions.readTimeout
import scalaj.http._

trait ZuoraRestClient {
  def makeRestGET(path: String): SerialisedJson

  def downloadFile(path: String): RawCSVText

  def makeRestPOST(path: String)(commandJSON: SerialisedJson): SerialisedJson
}

final case class ZuoraRestConfig(
                                  zuoraApiHost: String,
                                  zuoraApiAccessKeyId: String,
                                  zuoraApiSecretAccessKey: String
                                )


object ZuoraAPIClientFromParameterStore extends LazyLogging {

  private val loadConfig = {
    val identity = AppIdentity.whoAmI(defaultAppName = "zuora-creditor")
    val config: Config = ConfigurationLoader.load(identity) {
      case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
    }
    ZuoraRestConfig(
      zuoraApiHost = config.getString("zuoraApiHost"),
      zuoraApiAccessKeyId = config.getString("zuoraApiAccessKeyId"),
      zuoraApiSecretAccessKey = config.getString("zuoraApiSecretAccessKey")
    )
  }

  private val zuoraApiTimeout = 10000
  private val zuraConfig = loadConfig

  import zuraConfig._

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
