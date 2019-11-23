package services.payments

import javax.inject.Singleton
import javax.inject.Inject
import play.api.Configuration
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import akka.actor.ActorSystem
import com.decidir.coretx.utils.JedisPoolProvider
import com.decidir.coretx.messaging.IdempotentStore
import play.Logger
import akka.actor.Props
import com.decidir.coretx.messaging.ConfigurableKafkaConsumerActor
import akka.actor.ActorRef
import com.decidir.coretx.domain.NrosTraceJsonFormat._
import play.api.libs.json.Json
import com.decidir.coretx.domain.UpdateNrosTrace


object LegacyNroTraceListener {
  val topic = "nrostrace-legacy-topic"
}

@Singleton
class LegacyNroTraceListener @Inject() (configuration: Configuration,
                                           kafkaConfigurationProvider: KafkaConfigurationProvider,
                                           actorSystem: ActorSystem,
                                           redisPoolProvider: JedisPoolProvider,
                                           ltxs: LegacyTransactionService,
                                           idempotentStore: IdempotentStore) {
  
   Logger.info("Iniciando listener de persistencia de nro_trace legacy")
  
   
  val topics =  List(LegacyNroTraceListener.topic)
  val bootstrapServers = configuration.getString("sps.kafka.bootstrapServers").getOrElse(throw new Exception("No se definieron bootstrap servers"))
  val listener = actorSystem.actorOf(Props(
    new ConfigurableKafkaConsumerActor(
          kafkaConfigurationProvider.consumerConf("legacy-nrotrace", true), 
          topics, 
          onRecord, None)))
  
  Logger.debug("Listener de persistencia de nro_trace legacy")
  
  def onRecord(key: Option[String], msg: String, consumerActor: Option[ActorRef]) = {
    
    // TODO Manejar error
    Json.parse(msg).validate[UpdateNrosTrace].fold(
      errors => Logger.error("Error en comando de base de datos: " + errors),
      dbmsg => {
        Logger.info("Recibido comando de base de datos " + dbmsg.idempotentId)
        try {
          if (!idempotentStore.verifyExecuted(dbmsg.idempotentId)) {
            Logger.info("Procesando mensaje " + dbmsg.idempotentId)
        	  ltxs.updateNroTrace(dbmsg.nrosTraceSite)
        	  idempotentStore.add(dbmsg.idempotentId)
          }
          else {
            Logger.error("Ignorando mensaje repetido: " + dbmsg.idempotentId)
          }
        }
        catch {
          case e: Throwable => {
        	  Logger.error("Error grave insertando actualizacion de nro_trace", e)
            // TODO enviar a topico de errores
            
          }
        }
      }
    )   
  }
}