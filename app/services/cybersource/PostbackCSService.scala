package services.cybersource

import javax.inject.Singleton
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.Configuration
import play.api.libs.json.Json
import com.decidir.notifications.KafkaNotificationsClient
import com.decidir.coretx.MDCHelperTraitCommon
import com.decidir.coretx.api.OperationExecutionResponse
import com.decidir.notifications.PostMessage
import com.decidir.coretx.api.OperationJsonFormats._

@Singleton
class PostbackCSService @Inject() (implicit context: ExecutionContext,
                                 configuration: Configuration,
                                 kafkaNotificationsClient: KafkaNotificationsClient) extends MDCHelperTraitCommon{
  
  val postbackTimeout = (configuration.getLong("sps.cybersource.postback.timeoutMillis").getOrElse(10000l))

  def doPost(oer: OperationExecutionResponse) = {
    logger.debug(s"PostbackCSService.doPost ")
    val pm: PostMessage = oer2PostMessage(oer)
    kafkaNotificationsClient.send(post = pm, postTopic = "cspostbacks")
    logger.info("Notificacion enviada a kafka")
  }
  
  def oer2PostMessage(oer: OperationExecutionResponse) = {
    val datosSites = oer.operationResource.flatMap(_.datos_site).getOrElse(throw new scala.Exception("Missing site info"))
    val url = datosSites.urlPost.getOrElse(throw new scala.Exception("Missing urlPost"))
    PostMessage(url = datosSites.urlPost.getOrElse(""), postBody = Json.toJson(oer).toString, timeoutSeconds = postbackTimeout.toInt)
  }
  
  
}