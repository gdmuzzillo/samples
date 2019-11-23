package kafka

import cakesolutions.kafka.KafkaProducer
import cakesolutions.kafka.KafkaProducer.Conf
import org.apache.kafka.common.serialization.StringSerializer
import cakesolutions.kafka.KafkaProducerRecord
import java.util.UUID


/**
 * @author martinpaoletta
 */
object ProducerTest extends App {
  
  println("Iniciando server")
//  val server = new KafkaServer()
//  server.startup()
  println("Server iniciado")
  
  
  // Create a org.apache.kafka.clients.producer.KafkaProducer
  val producer = KafkaProducer(
      Conf(new StringSerializer(), new StringSerializer(), bootstrapServers = "localhost:9092")
  ) 
 
  for(i <- 0 to 1) {
    
    val recordId = UUID.randomUUID().toString
        
    val record = KafkaProducerRecord("hitopic", recordId, "hola mundo " + i)
    
    println("Enviando")
    producer.send(record)
  }
  
  producer.flush()
  
//  server.close()
  
  
}