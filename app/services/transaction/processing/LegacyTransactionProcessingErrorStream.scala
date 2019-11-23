package services.transaction.processing

import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.kafka.ConsumerSettings
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import services.payments.LegacyTxJsonFormat._
import akka.kafka.Subscriptions
import play.api.libs.json.Json
import javax.inject.Singleton
import javax.inject.Inject
import com.decidir.coretx.messaging.IdempotentStore
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import scala.concurrent.Future
import akka.kafka.scaladsl.Consumer
import play.api.Configuration
import scala.concurrent.duration._
import java.sql.SQLTransientException
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import com.decidir.coretx.messaging.MessagingRecord
import scala.util.Try
import akka.kafka.ConsumerMessage.CommittableMessage
import play.api.libs.json.JsValue
import scala.util.Success
import scala.util.Failure
import redis.clients.jedis.exceptions.JedisException
import services.payments.LegacyDBMessage
import services.payments.LegacyTransactionService
import controllers.MDCHelperTrait

object LegacyTransactionDeadLettersStream {
  
  val topic = "legacy-transactions-dead-letters-topic"
  
}

@Singleton
class LegacyTransactionProcessingErrorStream @Inject() (configuration: Configuration, 
                                                        ltxs: LegacyTransactionService, 
                                                        idempotentStore: IdempotentStore, 
                                                        kafkaConfigurationProvider: KafkaConfigurationProvider)
                                                        (implicit system: ActorSystem) extends MDCHelperTrait {

  val bootstrapServers = configuration.getString("sps.kafka.bootstrapServers").getOrElse(throw new Exception("No se definieron bootstrap servers"))

  implicit val actorMaterializer = ActorMaterializer()
  
  
  case class Test(nombre:String, info: Option[String]) extends scala.Serializable
  
  private val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
  .withBootstrapServers(bootstrapServers)
  .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  .withGroupId("legacy-transactions-error")
    
  private def getJson(msg: CommittableMessage[String, String]): Try[JsValue] = Try {
    //TODO: remover el dia q no queden datos mal formados en topico de errores
    Json.parse(msg.record.value())
  }  

  // Consume los updates de cliente y los impacta en CAT
  val pipeline =
    Consumer.committableSource(consumerSettings, Subscriptions.topics(LegacyTransactionStream.topic))
      .mapAsync(1) { msg =>
        getJson(msg) match {
          case Success(jsValue) => {
            jsValue.validate[LegacyDBMessage].fold(
              errors => {
                clearMDC
                logger.error(s"Processing Error topic - Error en comando de base de datos: ${msg}", errors)
              },
              dbmsg => {
                loadMDCFromLegacyDBMessage(dbmsg)
                logger.info(s"Processing Error topic - Recibido comando de base de datos ${dbmsg.idempotentId}")
                try {
                  if (!idempotentStore.verifyExecuted(dbmsg.idempotentId)) {
                    logger.info(s"Processing Error topic - Procesando mensaje - idempotentId: ${dbmsg.idempotentId}")
                    ltxs.handle(dbmsg.payload)
                    idempotentStore.add(dbmsg.idempotentId)
                  } else {
                    logger.error(s"Processing Error topic - Ignorando mensaje repetido idempotentId: ${dbmsg.idempotentId}")
                  }
                } catch {
                  case error @ (_ : SQLTransientException | _ : JedisException) => {
                    logger.error(s"Processing Error topic - Error grave insertando actualizacion de estado - Se va a reintentar. msg: $msg", error)
                    throw error
                  }
                  case error: Throwable => {
                    logger.error(s"Processing Error topic - Error grave insertando actualizacion de estado. No se reintenta msg - Enviando mensaje a topic ${LegacyTransactionDeadLettersStream.topic} - msg: $msg", error)
                    kafkaConfigurationProvider.defaultProducer.sendMessageToTopic(MessagingRecord(Some(dbmsg.payload.chargeId.toString), Json.toJson(dbmsg).toString), LegacyTransactionDeadLettersStream.topic)
                    kafkaConfigurationProvider.defaultProducer.sendMessageToTopic(MessagingRecord(Some(dbmsg.payload.chargeId.toString), Json.toJson(dbmsg).toString), "message-processing-notification-topic")
                    idempotentStore.add(dbmsg.idempotentId) //Se agrega para que no se reprocese en otro momento
                  }
                }
              })
          }
          case Failure(error) => {
            logger.error(s"Processing Error topic - Error grave procesando parse json, descartado de topic ${LegacyTransactionDeadLettersStream.topic} - msg: ${msg}", error)
          }
        }

        Future.successful(msg.committableOffset)
        
      }.mapMaterializedValue(_ => akka.NotUsed)
      
    val done = 
      pipeline.recoverWithRetries(-1, { case e => 
        logger.error("Error en flujo de Legacy Transaction Processing Error", e)
        pipeline.initialDelay(10 seconds)
      })
      .batch(max = 10, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) => batch.updated(elem) }
      .mapAsync(3)(_.commitScaladsl())
    
    .runWith(Sink.ignore)
      

}