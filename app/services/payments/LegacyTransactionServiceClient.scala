package services.payments

import akka.actor.ActorSystem
import javax.inject.Inject
import javax.inject.Singleton
import akka.actor.Props
import akka.routing.RoundRobinPool
import com.decidir.coretx.api.LegacyDBCommandTrait
import com.decidir.coretx.messaging.KafkaConfigurationProvider
import play.api.libs.json.Json
import com.decidir.coretx.messaging.RecordWithId
import com.decidir.coretx.messaging.MessagingRecord
import com.decidir.coretx.messaging.IdempotentStore
import com.decidir.coretx.domain.OperationRepository
import services.transaction.processing.LegacyTransactionListener

trait LegacyTransactionServiceClient {

  def insert(tx: InsertTx): Unit
  def insert(dtx: InsertDistributedTx): Unit
  def update(tx:UpdateTxOnOperation): Unit
  def update(tx: UpdateTx): Unit
//  def update(tx: UpdateReverse): Unit
  def insert(csr:InsertCS): Unit
  def update(csr:UpdateCS): Unit
  def insert(csrHistorico:InsertTxHistorico): Unit
  def update(uxref:UpdateXref): Unit
  def update(udtx:UpdateDistributedTx):Unit
  
}

@Singleton
class LegacyTransactionServiceProducer @Inject() (kafkaConfigurationProvider: KafkaConfigurationProvider, idempotentStore: IdempotentStore) extends LegacyTransactionServiceClient {

  val producer = kafkaConfigurationProvider.defaultProducer
  val topic = LegacyTransactionListener.topic
  import LegacyTxJsonFormat._
  
  private def sendMessage(command: LegacyDBCommandTrait) = {
    val id = idempotentStore.createId("legacytx")
    val msg = LegacyDBMessage(command, id)
    val json = Json.toJson(msg)
    producer.sendMessageToTopic(MessagingRecord(Some(command.merchantTransactionId), json.toString), topic)
  }
  
  def insert(tx: InsertTx): Unit = sendMessage(tx)
  def insert(dtx: InsertDistributedTx): Unit = sendMessage(dtx)
  def update(tx:UpdateTxOnOperation): Unit = sendMessage(tx)
  def update(tx: UpdateTx): Unit = sendMessage(tx)
  def insert(csr:InsertCS):Unit = sendMessage(csr)
  def update(csr:UpdateCS):Unit = sendMessage(csr)
  def insert(csrHistorico:InsertTxHistorico): Unit = sendMessage(csrHistorico)
  def update(uxref:UpdateXref):Unit = sendMessage(uxref)
  def update(udtx:UpdateDistributedTx):Unit = sendMessage(udtx)
//  def update(tx: UpdateReverse): Unit = sendMessage(tx)
}



