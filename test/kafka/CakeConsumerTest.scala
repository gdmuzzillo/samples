package kafka

import scala.concurrent.duration._
import cakesolutions.kafka.KafkaConsumer
import cakesolutions.kafka.akka.KafkaConsumerActor
import cakesolutions.kafka.KafkaConsumer.Conf
import org.apache.kafka.common.serialization.StringDeserializer
import akka.actor.ActorSystem
import org.apache.kafka.common.network.Receive
import cakesolutions.kafka.akka.KafkaConsumerActor.Confirm
import cakesolutions.kafka.akka.ConsumerRecords
import akka.actor.Actor
import akka.actor.Props
import cakesolutions.kafka.akka.KafkaConsumerActor.Subscribe

/**
 * @author martinpaoletta
 */
object  CakeConsumerTest extends App {
  
  implicit val system = ActorSystem("main")
    
  
  val receiver = system.actorOf(Props[ReceiverActor])

  receiver ! "prueba"
  
  
  
}


class ReceiverActor extends Actor {

  // Configuration for the KafkaConsumer
  val consumerConf = KafkaConsumer.Conf(
      new StringDeserializer,
      new StringDeserializer,
      bootstrapServers = "localhost:9092",
      groupId = "groupId",
      enableAutoCommit = false)
  
  // Configuration specific to the Async Consumer Actor
  val actorConf = KafkaConsumerActor.Conf(List("hitopic"), 1.seconds, 3.seconds)
  
  // Create the Actor
  val consumer = context.actorOf(
    KafkaConsumerActor.props(consumerConf, actorConf, self)
  )  
  
  // Extractor for ensuring type safe cast of records
  val recordsExt = ConsumerRecords.extractor[String, String]

  consumer ! Subscribe()
  
  override def receive: Receive = {
    
    case string: String => println(string)
    
    // Type safe cast of records to correct serialisation type
    case recordsExt(records) =>
      processRecords(records.pairs)
      sender() ! Confirm(records.offsets)
      
      
//    case other => {
//      println("Otra cosa: " + other)
//    }
  }

  // Process the whole batch of received records.
  // The first value in the tuple is the optional key of a record.
  // The second value in the tuple is the actual value from a record.
  def processRecords(records: Seq[(Option[String], String)]) = {
    
    println("processRecords: " + records.size)
    
    records.foreach(println)
    
  }
  
}
