package com.decidir.coretx.domain
import scala.collection.JavaConverters._
import com.decidir.coretx.utils.JedisUtils
import scala.annotation.tailrec
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

/**
 * @author martinpaoletta
 */
object PruebaReintentosEnRedis extends App { //with JedisUtils {
  
//  val jedisPool = pool("127.0.0.1", 6379)
//  
//  val sites = 0 to 600
//  sites.foreach{siteId => 
//    val txsKey = s"sites:$siteId:txs"
//    val txs = 1 to 1000000
//    val map = txs.foldLeft(Map[String, String]()){(map, txId) =>
//      if(txId % 50 == 0) {
//        map + (txId.toString -> ((txId % 3) +1).toString)
//      }
//      else map + (txId.toString -> "1")
//    }
//    
//    @tailrec
//    def eat(map: Map[String, String]): Unit = {
//      val (submap, rest) = map.splitAt(500000)
//      val ini = System.currentTimeMillis()
//      doWithRedisTx { redis => 
//        submap.foreach {txReps =>
//          val (txId, reps) = txReps
//          redis.sadd(txsKey, txId)
//          if(reps.toInt > 1) redis.hmset(txsKey + ":reps", Map(txId -> reps).asJava)
//        }
//      }        
//      if(!rest.isEmpty) eat(rest)
//    }
//    
//    eat(map)
//
//  }
//  
//  
//  def pool(redisHost: String, redisPort: Int) = {
//    val readTimeout = 10000
//    val writeTimeout = 10000
//    val poolSize = 10
//    val poolConfig = new JedisPoolConfig()
//    poolConfig.setMaxTotal(poolSize)
//    poolConfig.setMaxWaitMillis(writeTimeout)
//    val pool = new JedisPool(poolConfig, redisHost, redisPort, readTimeout);
//
//    val jedis = pool.getResource()
//    try {
//      val value = jedis.info
//      pool.returnResource(jedis)
//    } catch {
//      case e: Exception => {
//        e.printStackTrace()
//        pool.returnBrokenResource(jedis)
//      }
//    }
//
//    pool
//  }
//  
  
  
}