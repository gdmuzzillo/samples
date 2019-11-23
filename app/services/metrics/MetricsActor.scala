package services.metrics

import akka.actor.Actor
import com.decidir.coretx.utils.JedisUtils
import redis.clients.jedis.JedisPool
import akka.actor.ActorSystem
import akka.actor.Props
import javax.inject.Inject
import javax.inject.Singleton
import com.decidir.coretx.utils.JedisPoolProvider
import controllers.MDCHelperTrait
import com.decidir.coretx.LoggingHelper
import redis.clients.util.Pool
import redis.clients.jedis.Jedis

@Singleton
class MetricsClient @Inject() (system: ActorSystem, jedisPoolProvider: JedisPoolProvider) {
  
  private val justRecord = JustRecordTx() 
  
	private val actor = system.actorOf(Props(new MetricsActor(jedisPoolProvider.get)))

  def recordInNanos(txId: String, appId: String, eventGroup: String, step: String, nanos: Long) = {
   actor ! MetricInNanosEvent(System.currentTimeMillis(), txId, appId, eventGroup, step, nanos, justRecord) 
  }

  def recordInNanos(timestamp: Long, txId: String, appId: String, eventGroup: String, step: String, nanos: Long) = {
   actor ! MetricInNanosEvent(timestamp, txId, appId, eventGroup, step, nanos, justRecord) 
  }

  def recordInMillis(txId: String, appId: String, eventGroup: String, step: String, millis: Long) = {
   actor ! MetricInMillisEvent(System.currentTimeMillis(), txId, appId, eventGroup, step, millis, justRecord) 
  }

  def recordInMillis(timestamp: Long, txId: String, appId: String, eventGroup: String, step: String, millis: Long) = {
   actor ! MetricInMillisEvent(timestamp, txId, appId, eventGroup, step, millis, justRecord) 
  }

  def recordInMillis(start: Long, txId: String, appId: String, eventGroup: String, step: String) = {
   val end = System.currentTimeMillis()
   actor ! MetricInMillisEvent(end, txId, appId, eventGroup, step, end - start, justRecord) 
  }
  
  
}



/**
 * @author martinpaoletta
 */
class MetricsActor(val jedisPool: Pool[Jedis]) extends Actor with JedisUtils{
  
  val metricsLogger = LoggingHelper;
  
  def receive = {
    
    case MetricInNanosEvent(timestamp, txId, appId, eventGroup, step, nanos, eventType) => 
      record(timestamp, txId, appId, eventGroup, step, nanos)
      
    case MetricInMillisEvent(timestamp, txId, appId, eventGroup, step, millis, eventType) => 
      record(timestamp, txId, appId, eventGroup, step, millis*1000000)
    
  }
  
  def record(timestamp: Long, txId: String, appId: String, eventGroup: String, step: String, nanos: Long) = {
    metricsLogger.logMetrics(timestamp, txId, appId, eventGroup, step, nanos)
  }
  
}

case class MetricInNanosEvent(timestamp: Long, txId: String, appId: String, eventGroup: String, step: String, nanos: Long, eventType: TxEvent)
case class MetricInMillisEvent(timestamp: Long, txId: String, appId: String, eventGroup: String, step: String, millis: Long, eventType: TxEvent)

sealed trait TxEvent
case class JustRecordTx() extends TxEvent
case class BeginTx() extends TxEvent
case class EndTx() extends TxEvent
