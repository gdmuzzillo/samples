package services

import javax.inject.Singleton
import javax.inject.Inject
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import com.decidir.coretx.messaging.MessagingRecord
import play.api.libs.json.Json
import com.decidir.coretx.api.CardTokenJsonFormats._
import com.decidir.coretx.api.OperationJsonFormats._
import com.decidir.coretx.api.{CardTokenMessage, CardTokenStore, OperationExecutionResponse, OperationResource}

@Singleton
class KafkaClient @Inject() (kafkaConfigurationProvider: KafkaConfigurationProvider){
  
  val producer = kafkaConfigurationProvider.defaultProducer
  
  def send(cardTokenMessage: CardTokenMessage, topic: String): Unit = {
    val json = Json.toJson(cardTokenMessage)
    producer.sendMessageToTopic(MessagingRecord(None, json.toString), topic)
  }
  
  def send(operationExecutionResponse: OperationExecutionResponse, topic: String): Unit = {
    val json = Json.toJson(operationExecutionResponse)
    producer.sendMessageToTopic(MessagingRecord(None, json.toString), topic)
  }
  
  def send(message: String, topic: String): Unit = {
    producer.sendMessageToTopic(MessagingRecord(None, message), topic)
  }
  
}