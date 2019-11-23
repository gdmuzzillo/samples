package services.cybersource

import javax.inject.Inject
import javax.inject.Singleton
import akka.actor.ActorSystem
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import play.api.Configuration
import akka.actor.Props
import play.Logger
import com.decidir.coretx.messaging.ConfigurableKafkaConsumerActor
import akka.actor.ActorRef
import com.decidir.coretx.api.ReviewCS
import play.api.libs.json.Json
import com.decidir.coretx.api.CybersourceJsonMessages.reviewStateReads


object CybersourceListener {
  
  val topic = "cybersource-topic"
  
}

@Singleton
class CybersourceListenerFactory @Inject() (configuration: Configuration,
                kafkaConfigurationProvider: KafkaConfigurationProvider,
                actorSystem: ActorSystem,
                cybersourceService: CybersourceService) {
  
  Logger.info("Iniciando listener de cambio de estado para Cybersource")

  val topics =  List(CybersourceListener.topic)
  val bootstrapServers = configuration.getString("sps.kafka.bootstrapServers").getOrElse(throw new Exception("No se definieron bootstrap servers"))
  val listener = actorSystem.actorOf(Props(
    new ConfigurableKafkaConsumerActor(
          kafkaConfigurationProvider.consumerConf("cybersource", true), 
          topics, 
          onRecord, None)))
  
  Logger.info("Listener de cambio de estado para Cybersource")
  
  
  def onRecord(key: Option[String], msg: String, consumerActor: Option[ActorRef]) = {
    Json.parse(msg).validate[ReviewCS].fold(
      errors => Logger.error("Error cs message: " + errors),
      reviewState => {
        Logger.info("Recive cs message: " + msg)
        cybersourceService.changeState(reviewState)   
      }
    )
  
  }
}