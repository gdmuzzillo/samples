package com.decidir.coretx.domain

import com.decidir.coretx.utils.JedisUtils
import com.decidir.coretx.utils.JedisPoolProvider
import javax.inject.Inject

/**
 * @author martinpaoletta
 */
class IdComercioLocationRepository @Inject() (jedisPoolProvider: JedisPoolProvider) extends JedisUtils {
  
  val jedisPool = jedisPoolProvider.get
  
  val key = "idcomerciolocation"
  
  def store(pair: Pair) = 
    doWithRedis { _.hset(key, pair.e1, pair.e2) }
    
  def getIdComercioLocation(idComercio: String): Option[String] = {
    
    val found = evalWithRedis { _.hget(key, idComercio) }
    if(found != null) Some(found) else None
    
  }  
  
}