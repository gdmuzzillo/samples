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
import redis.clients.jedis.exceptions.JedisException
import services.payments.LegacyDBMessage
import services.payments.LegacyTransactionService
import controllers.MDCHelperTrait

object LegacyTransactionStream {
  
  val topic = "legacy-transactions-error-topic"
  
}

@Singleton
class LegacyTransactionStream @Inject()(configuration: Configuration, 
                                        ltxs: LegacyTransactionService, 
                                        idempotentStore: IdempotentStore,
                                        kafkaConfigurationProvider: KafkaConfigurationProvider)
                                        (implicit system: ActorSystem) extends MDCHelperTrait {

  val bootstrapServers = configuration.getString("sps.kafka.bootstrapServers").getOrElse(throw new Exception("No se definieron bootstrap servers"))

  implicit val actorMaterializer = ActorMaterializer()
  
  private val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    .withGroupId("legacy-transactions")

  // Consume los updates de cliente y los impacta en CAT
  val pipeline =
    Consumer.committableSource(consumerSettings, Subscriptions.topics(LegacyTransactionListener.topic))
      .mapAsync(1) { msg =>
        Json.parse(msg.record.value()).validate[LegacyDBMessage].fold(
          errors => {
            clearMDC
            logger.error(s"Error en comando de base de datos: ${msg}", errors)
          },
          dbmsg => {
            loadMDCFromLegacyDBMessage(dbmsg)
            logger.info(s"Recibido comando de base de datos ${dbmsg.idempotentId}")
            try {
              if (!idempotentStore.verifyExecuted(dbmsg.idempotentId)) {
                logger.info(s"Procesando mensaje - idempotentId: ${dbmsg.idempotentId}")
                ltxs.handle(dbmsg.payload)
                idempotentStore.add(dbmsg.idempotentId)
              } else {
                logger.error(s"Ignorando mensaje repetido idempotentId: ${dbmsg.idempotentId}")
              }
            } catch {
              case error @ (_ : SQLTransientException | _ : JedisException) => {
                logger.error(s"Error grave insertando actualizacion de estado - Se va a reintentar. msg: $msg", error)
                throw error
              }
              case error: Throwable => {
                logger.error(s"Error grave insertando actualizacion de estado. No se reintenta msg - Enviando mensaje a topic ${LegacyTransactionStream.topic} - msg: $msg", error)
                kafkaConfigurationProvider.defaultProducer.sendMessageToTopic(MessagingRecord(Some(dbmsg.payload.chargeId.toString), Json.toJson(dbmsg).toString), LegacyTransactionStream.topic)
              }
            }
          })

        Future.successful(msg.committableOffset)
        
      }.mapMaterializedValue(_ => akka.NotUsed)
      
    val done = 
      pipeline.recoverWithRetries(-1, { case e => 
        logger.error("Error en flujo de Legacy Transaction", e)
        pipeline.initialDelay(10 seconds)
      })
      .batch(max = 10, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) => batch.updated(elem)
      }
      .mapAsync(3)(_.commitScaladsl())
    
    .runWith(Sink.ignore)
      

}