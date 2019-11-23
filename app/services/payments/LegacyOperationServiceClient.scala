package services.payments

import com.decidir.coretx.api.LegacyDBCommandTrait
import com.decidir.coretx.domain.OperationRepository
import com.decidir.coretx.messaging.IdempotentStore
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import com.decidir.coretx.messaging.MessagingRecord
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.routing.RoundRobinPool
import javax.inject.Inject
import javax.inject.Singleton
import play.api.libs.json.Json
import services.transaction.processing.LegacyTransactionListener

trait LegacyOperationServiceClient {

  def insert(opx: InsertOpx): Unit
  def insert(dopx: InsertDOPx): Unit
  def insert(copx: InsertConfirmationOpx): Unit
  
}

@Singleton
class LegacyOperationServiceActorClient @Inject() (system: ActorSystem, operationRepository: OperationRepository) extends LegacyOperationServiceClient {
  
 val actor = system.actorOf(RoundRobinPool(1).props(Props(new LegacyOperationServiceActor(operationRepository))), "legacyOperationService")

  def insert(opx: InsertOpx): Unit = actor ! opx
  def insert(dopx: InsertDOPx): Unit = actor ! dopx
  def insert(copx: InsertConfirmationOpx): Unit = actor ! copx
  
}

@Singleton
class LegacyOperationServiceProducer @Inject() (kafkaConfigurationProvider: KafkaConfigurationProvider, idempotentStore: IdempotentStore) extends LegacyOperationServiceClient {

  val producer = kafkaConfigurationProvider.defaultProducer
  val topic = LegacyTransactionListener.topic //  usamos el listener de transacciones para que se respete el orden de llegada entre operaciones y transacciones
  import LegacyTxJsonFormat._
  
  private def sendMessage(command: LegacyDBCommandTrait) = {
    val id = idempotentStore.createId("legacytx")
    val msg = LegacyDBMessage(command, id)
    val json = Json.toJson(msg)
    producer.sendMessageToTopic(MessagingRecord(Some(command.merchantTransactionId), json.toString), topic)
  }
  
  def insert(opx: InsertOpx): Unit = sendMessage(opx)
  def insert(dopx: InsertDOPx): Unit = sendMessage(dopx)
  def insert(dopx: InsertConfirmationOpx): Unit = sendMessage(dopx)

}
